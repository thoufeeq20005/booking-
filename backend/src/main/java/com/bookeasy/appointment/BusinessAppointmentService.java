package com.bookeasy.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.appointment.Appointment.Party;
import com.bookeasy.audit.AuditService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.service.OrganizationAccess;
import com.bookeasy.staff.StaffProfile;
import com.bookeasy.staff.StaffProfileRepository;

/**
 * The business side of bookings. Owners act on every booking of their business; staff act on their own bookings,
 * confirming/declining only with CAN_CONFIRM and cancelling only with CAN_CANCEL.
 */
@Service
public class BusinessAppointmentService {

    public enum Action { CONFIRM, DECLINE, CANCEL, CHECK_IN, COMPLETE, NO_SHOW }

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");

    private final AppointmentRepository appointments;
    private final BookingService bookings;
    private final SlotLocks locks;
    private final QrCodes qr;
    private final OrganizationAccess access;
    private final StaffProfileRepository staff;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BusinessAppointmentService(AppointmentRepository appointments, BookingService bookings, SlotLocks locks,
                                      QrCodes qr, OrganizationAccess access, StaffProfileRepository staff,
                                      AuditService audit, ApplicationEventPublisher events, Clock clock) {
        this.appointments = appointments;
        this.bookings = bookings;
        this.locks = locks;
        this.qr = qr;
        this.access = access;
        this.staff = staff;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // ----- owner -----

    @Transactional(readOnly = true)
    public List<AppointmentView> ownerDay(long userId, long orgId, LocalDate date, Long branchId) {
        Organization org = access.owned(userId, orgId);
        Instant from = date.atStartOfDay(DEFAULT_ZONE).toInstant();
        return bookings.views(appointments.forOrganization(org.getId(), branchId, from,
                date.plusDays(1).atStartOfDay(DEFAULT_ZONE).toInstant()), false);
    }

    @Transactional
    public AppointmentView ownerAct(long userId, long orgId, long appointmentId, Action action, String reason) {
        Organization org = access.owned(userId, orgId);
        Appointment a = appointments.findByIdForUpdate(appointmentId)
                .filter(x -> x.getOrganizationId().equals(org.getId()))
                .orElseThrow(() -> ApiException.notFound("Booking"));
        return apply(a, action, reason, userId, "BUSINESS_OWNER");
    }

    /** Check-in by scanning the customer's pass. */
    @Transactional
    public AppointmentView ownerCheckIn(long userId, long orgId, String code) {
        Organization org = access.owned(userId, orgId);
        String reference = qr.verify(code);
        Appointment a = reference == null ? null : appointments.findByReference(reference)
                .filter(x -> x.getOrganizationId().equals(org.getId())).orElse(null);
        if (a == null) {
            throw new ApiException(ErrorCode.INVALID_QR_CODE);
        }
        Appointment locked = appointments.findByIdForUpdate(a.getId()).orElseThrow();
        return apply(locked, Action.CHECK_IN, null, userId, "BUSINESS_OWNER");
    }

    // ----- staff -----

    @Transactional(readOnly = true)
    public List<AppointmentView> staffDay(long userId, LocalDate date) {
        List<Long> mine = staff.findByUserIdAndStatus(userId, StaffProfile.Status.ACTIVE).stream().map(StaffProfile::getId).toList();
        if (mine.isEmpty()) {
            return List.of();
        }
        return bookings.views(appointments.forStaff(mine, date.atStartOfDay(DEFAULT_ZONE).toInstant(),
                date.plusDays(1).atStartOfDay(DEFAULT_ZONE).toInstant()), false);
    }

    @Transactional
    public AppointmentView staffAct(long userId, long appointmentId, Action action, String reason) {
        Appointment a = appointments.findByIdForUpdate(appointmentId).orElseThrow(() -> ApiException.notFound("Booking"));
        StaffProfile me = a.getStaffId() == null ? null : staff.findById(a.getStaffId())
                .filter(s -> Objects.equals(s.getUserId(), userId) && s.getStatus() == StaffProfile.Status.ACTIVE)
                .orElse(null);
        if (me == null) {
            throw ApiException.notFound("Booking");
        }
        boolean allowed = switch (action) {
            case CONFIRM, DECLINE -> me.can(StaffProfile.Permission.CAN_CONFIRM);
            case CANCEL -> me.can(StaffProfile.Permission.CAN_CANCEL);
            case CHECK_IN, COMPLETE, NO_SHOW -> true;
        };
        if (!allowed) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return apply(a, action, reason, userId, "STAFF");
    }

    private AppointmentView apply(Appointment a, Action action, String reason, long actorId, String role) {
        Instant now = clock.instant();
        String why = reason == null || reason.isBlank() ? null : reason.strip();
        AppointmentChanged.Kind kind;
        switch (action) {
            case CONFIRM -> { a.confirm(now); kind = AppointmentChanged.Kind.CONFIRMED; }
            case DECLINE -> {
                requireReason(why);
                a.decline(why, now);
                locks.release(a.getId());
                kind = AppointmentChanged.Kind.DECLINED;
            }
            case CANCEL -> {
                requireReason(why);
                // A business cancellation always refunds everything the customer paid.
                a.cancel(Party.BUSINESS, why, a.getStatus() == Appointment.Status.PENDING_PAYMENT ? null : a.getTotalAmount(), now);
                locks.release(a.getId());
                kind = AppointmentChanged.Kind.CANCELLED;
            }
            case CHECK_IN -> { a.checkIn(now); kind = null; }
            case COMPLETE -> { a.complete(now); kind = AppointmentChanged.Kind.COMPLETED; }
            case NO_SHOW -> { a.noShow(now); kind = AppointmentChanged.Kind.NO_SHOW; }
            default -> throw new IllegalStateException();
        }
        if (action == Action.DECLINE || action == Action.CANCEL || action == Action.NO_SHOW) {
            audit.record(actorId, role, "APPOINTMENT_" + action.name(), "APPOINTMENT", String.valueOf(a.getId()),
                    why == null ? Map.of("reference", a.getReference()) : Map.of("reference", a.getReference(), "reason", why), null);
        }
        if (kind != null) {
            events.publishEvent(new AppointmentChanged(a.getId(), kind));
        }
        return bookings.view(a, false);
    }

    private static void requireReason(String why) {
        if (why == null) {
            throw ApiException.invalidField("reason", "NotBlank", "Tell the customer why");
        }
    }
}
