package com.bookeasy.appointment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.appointment.Appointment.Party;
import com.bookeasy.appointment.Appointment.Status;
import com.bookeasy.availability.SlotService;
import com.bookeasy.availability.SlotService.DaySlots;
import com.bookeasy.availability.SlotService.SlotView;
import com.bookeasy.catalog.ServiceOffering;
import com.bookeasy.catalog.ServiceRepository;
import com.bookeasy.common.crypto.CryptoService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.ratelimit.RateLimiter;
import com.bookeasy.organization.entity.Branch;
import com.bookeasy.organization.entity.ConfirmationMode;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.repository.BranchRepository;
import com.bookeasy.organization.repository.OrganizationRepository;
import com.bookeasy.platform.PlatformConfigService;
import com.bookeasy.staff.StaffProfile;
import com.bookeasy.staff.StaffProfileRepository;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;

/** Customer side of booking: create, list, cancel (with refund preview) and reschedule. */
@Service
public class BookingService {

    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final AppointmentRepository appointments;
    private final SlotService slots;
    private final SlotLocks locks;
    private final QrCodes qr;
    private final OrganizationRepository organizations;
    private final BranchRepository branches;
    private final ServiceRepository services;
    private final StaffProfileRepository staff;
    private final UserRepository users;
    private final PlatformConfigService settings;
    private final PaymentGate paymentGate;
    private final CryptoService crypto;
    private final ApplicationEventPublisher events;
    private final RateLimiter rateLimiter;
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public BookingService(AppointmentRepository appointments, SlotService slots, SlotLocks locks, QrCodes qr,
                          OrganizationRepository organizations, BranchRepository branches, ServiceRepository services,
                          StaffProfileRepository staff, UserRepository users, PlatformConfigService settings,
                          PaymentGate paymentGate, CryptoService crypto, ApplicationEventPublisher events, RateLimiter rateLimiter,
                          org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.appointments = appointments;
        this.slots = slots;
        this.locks = locks;
        this.qr = qr;
        this.organizations = organizations;
        this.branches = branches;
        this.services = services;
        this.staff = staff;
        this.users = users;
        this.settings = settings;
        this.paymentGate = paymentGate;
        this.crypto = crypto;
        this.events = events;
        this.rateLimiter = rateLimiter;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record BookRequest(long branchId, long serviceId, Long staffId, Instant startsAt, String note, Long familyMemberId) {
    }

    public record CancelPreview(BigDecimal totalPaid, BigDecimal refundAmount, int refundPercent, boolean allowed) {
    }

    @Transactional
    public AppointmentView book(long customerId, BookRequest req) {
        rateLimiter.hit("book:user:" + customerId, 20, Duration.ofHours(1));
        // Any signed-in customer may book; email/phone verification is not required.
        User customer = users.findById(customerId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        Branch branch = branches.findById(req.branchId()).orElseThrow(() -> ApiException.notFound("Branch"));
        ServiceOffering service = services.findById(req.serviceId()).orElseThrow(() -> ApiException.notFound("Service"));
        Chosen chosen = choose(branch, service, req.startsAt(), req.staffId());
        Organization org = organizations.findById(branch.getOrganizationId()).orElseThrow();

        Instant now = clock.instant();
        BigDecimal price = chosen.day().price().setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = price.multiply(settings.decimal(PlatformConfigService.COMMISSION_PERCENT))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        Instant end = chosen.slot().endsAt();
        Instant blocked = end.plus(Duration.ofMinutes(service.getBufferMinutes()));
        Appointment a = new Appointment(newReference(), customerId, org.getId(), branch.getId(), service.getId(),
                chosen.staffId(), chosen.slot().startsAt(), end, blocked, Status.PENDING_PAYMENT, service.getName(), price, fee,
                blank(req.note()), now);
        if (req.familyMemberId() != null) {
            a.bookFor(req.familyMemberId(), familyName(customerId, req.familyMemberId()));
        }
        appointments.saveAndFlush(a);
        locks.claim(SlotLocks.resourceKey(chosen.staffId(), branch.getId(), service.getId()), a.getStartsAt(), blocked, a.getId());
        if (!paymentGate.requiresOnlinePayment(a)) {
            a.markPaid(org.getConfirmationMode() == ConfirmationMode.APPROVAL, now);
            events.publishEvent(new AppointmentChanged(a.getId(), AppointmentChanged.Kind.BOOKED));
        }
        return view(a, true);
    }

    /** The customer's history, limited to the retention window (platform setting retention.booking_history_years). */
    @Transactional(readOnly = true)
    public List<AppointmentView> mine(long customerId) {
        Instant since = clock.instant().atZone(ZoneId.of("Asia/Kolkata"))
                .minusYears(settings.integer("retention.booking_history_years")).toInstant();
        return views(appointments.findByCustomerIdOrderByStartsAtDesc(customerId).stream()
                .filter(a -> a.getStartsAt().isAfter(since)).toList(), false);
    }

    private String familyName(long customerId, long familyMemberId) {
        return jdbc.getJdbcTemplate().query("SELECT full_name FROM family_members WHERE id = ? AND user_id = ?",
                        (rs, i) -> rs.getString(1), familyMemberId, customerId).stream().findFirst()
                .orElseThrow(() -> ApiException.invalidField("familyMemberId", "INVALID", "Choose one of your family members"));
    }

    @Transactional(readOnly = true)
    public AppointmentView mine(long customerId, long id) {
        return view(owned(customerId, id), true);
    }

    @Transactional(readOnly = true)
    public CancelPreview cancelPreview(long customerId, long id) {
        return preview(owned(customerId, id));
    }

    @Transactional
    public AppointmentView cancel(long customerId, long id, String reason) {
        Appointment a = appointments.findByIdForUpdate(id).filter(x -> x.getCustomerId() == customerId)
                .orElseThrow(() -> ApiException.notFound("Booking"));
        CancelPreview p = preview(a);
        if (!p.allowed() || (a.isBill() && a.getStatus() != Status.PENDING_PAYMENT)) {
            // A paid bill is settled with the restaurant; it can't be cancelled here.
            throw new ApiException(ErrorCode.BOOKING_NOT_CHANGEABLE);
        }
        a.cancel(Party.CUSTOMER, blank(reason), a.getStatus() == Status.PENDING_PAYMENT ? null : p.refundAmount(), clock.instant());
        locks.release(a.getId());
        events.publishEvent(new AppointmentChanged(a.getId(), AppointmentChanged.Kind.CANCELLED));
        return view(a, true);
    }

    @Transactional
    public AppointmentView reschedule(long customerId, long id, Instant startsAt, Long staffId) {
        Appointment a = appointments.findByIdForUpdate(id).filter(x -> x.getCustomerId() == customerId)
                .orElseThrow(() -> ApiException.notFound("Booking"));
        if (a.getShowtimeId() != null || a.getPartySize() != null || a.isBill()) {
            // Tickets and table bookings are cancelled and booked again rather than moved.
            throw new ApiException(ErrorCode.BOOKING_NOT_CHANGEABLE);
        }
        Instant now = clock.instant();
        if (!a.isActive() || a.getStartsAt().isBefore(now.plus(Duration.ofHours(
                settings.integer("refund.partial_before_hours"))))) {
            throw new ApiException(ErrorCode.BOOKING_NOT_CHANGEABLE);
        }
        if (a.getRescheduleCount() >= settings.integer("booking.max_reschedules")) {
            throw new ApiException(ErrorCode.RESCHEDULE_LIMIT_REACHED);
        }
        Branch branch = branches.findById(a.getBranchId()).orElseThrow();
        ServiceOffering service = services.findById(a.getServiceId()).orElseThrow();
        // The new time must be free on its own: the booking's current time still counts as busy until it moves.
        locks.release(a.getId());
        appointments.flush();
        Chosen chosen = choose(branch, service, startsAt, staffId);
        Instant blocked = chosen.slot().endsAt().plus(Duration.ofMinutes(service.getBufferMinutes()));
        a.reschedule(chosen.staffId(), chosen.slot().startsAt(), chosen.slot().endsAt(), blocked, now);
        locks.claim(SlotLocks.resourceKey(chosen.staffId(), branch.getId(), service.getId()), a.getStartsAt(), blocked, a.getId());
        events.publishEvent(new AppointmentChanged(a.getId(), AppointmentChanged.Kind.RESCHEDULED));
        return view(a, true);
    }

    // ----- helpers -----

    private record Chosen(DaySlots day, SlotView slot, Long staffId) {
    }

    /** The requested start must be one of the slots the customer could see, for the requested (or any) staff member. */
    private Chosen choose(Branch branch, ServiceOffering service, Instant startsAt, Long staffId) {
        if (startsAt == null) {
            throw ApiException.invalidField("startsAt", "NotNull", "Choose a time");
        }
        LocalDate date = LocalDate.ofInstant(startsAt, ZoneId.of(branch.getTimezone()));
        DaySlots day = slots.publicSlots(branch.getId(), service.getId(), date, staffId);
        SlotView slot = day.slots().stream().filter(s -> s.startsAt().equals(startsAt)).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.SLOT_UNAVAILABLE));
        Long chosenStaff = null;
        if (service.isRequiresStaff()) {
            chosenStaff = staffId != null ? staffId : slot.staffIds().get(0);
            if (!slot.staffIds().contains(chosenStaff)) {
                throw new ApiException(ErrorCode.SLOT_UNAVAILABLE);
            }
        }
        return new Chosen(day, slot, chosenStaff);
    }

    /** Refund policy for customer cancellations; the platform fee is not refunded when the customer cancels. */
    CancelPreview preview(Appointment a) {
        Instant now = clock.instant();
        if (!a.isActive() || !a.getStartsAt().isAfter(now)) {
            return new CancelPreview(a.getTotalAmount(), BigDecimal.ZERO.setScale(2), 0, false);
        }
        long hoursLeft = Duration.between(now, a.getStartsAt()).toMinutes() / 60;
        int percent = hoursLeft >= settings.integer("refund.full_before_hours") ? 100
                : hoursLeft >= settings.integer("refund.partial_before_hours") ? settings.integer("refund.partial_percent") : 0;
        BigDecimal refund = a.getServicePrice().multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new CancelPreview(a.getTotalAmount(), refund, percent, true);
    }

    private Appointment owned(long customerId, long id) {
        return appointments.findById(id).filter(a -> a.getCustomerId() == customerId)
                .orElseThrow(() -> ApiException.notFound("Booking"));
    }

    private String newReference() {
        for (int attempt = 0; attempt < 5; attempt++) {
            byte[] b = crypto.randomBytes(8);
            StringBuilder sb = new StringBuilder("BE");
            for (byte x : b) {
                sb.append(REF_ALPHABET.charAt((x & 0xFF) % REF_ALPHABET.length()));
            }
            String ref = sb.toString();
            if (appointments.findByReference(ref).isEmpty()) {
                return ref;
            }
        }
        throw new IllegalStateException("Could not generate a unique booking reference");
    }

    // ----- views (shared with the business side) -----

    AppointmentView view(Appointment a, boolean withQr) {
        return views(List.of(a), withQr).get(0);
    }

    /** A movie ticket or table booking, created by the venue services inside their own transaction. */
    public record VenueBooking(long customerId, long organizationId, long branchId, long serviceId, Instant startsAt,
                               Instant endsAt, String title, BigDecimal price, String note, Long showtimeId,
                               Integer partySize, boolean noPlatformFee) {
    }

    /** Saves the booking (holding nothing yet); the caller claims seats/tables, then calls {@link #finishVenueBooking}. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Appointment createVenueBooking(VenueBooking v) {
        rateLimiter.hit("book:user:" + v.customerId(), 20, Duration.ofHours(1));
        BigDecimal price = v.price().setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = v.noPlatformFee() ? BigDecimal.ZERO.setScale(2) : price.multiply(settings.decimal(PlatformConfigService.COMMISSION_PERCENT))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        Appointment a = new Appointment(newReference(), v.customerId(), v.organizationId(), v.branchId(), v.serviceId(),
                null, v.startsAt(), v.endsAt(), v.endsAt(), Status.PENDING_PAYMENT, v.title(), price, fee, blank(v.note()),
                clock.instant());
        if (v.showtimeId() != null) {
            a.forShowtime(v.showtimeId());
        }
        if (v.partySize() != null) {
            a.forParty(v.partySize());
        }
        return appointments.saveAndFlush(a);
    }

    /** Free bookings (tables) are confirmed now; paid ones wait on the payment page. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public AppointmentView finishVenueBooking(Appointment a) {
        if (!paymentGate.requiresOnlinePayment(a)) {
            boolean approval = organizations.findById(a.getOrganizationId())
                    .map(o -> o.getConfirmationMode() == ConfirmationMode.APPROVAL).orElse(false);
            a.markPaid(approval, clock.instant());
            events.publishEvent(new AppointmentChanged(a.getId(), AppointmentChanged.Kind.BOOKED));
        }
        return view(a, true);
    }

    /** The booking behind a scanned pass (QR link or payload); unknown or forged codes are rejected. */
    @Transactional(readOnly = true)
    public BookingPass pass(String code) {
        String reference = qr.verify(code);
        Appointment a = reference == null ? null : appointments.findByReference(reference).orElse(null);
        if (a == null) {
            throw new ApiException(ErrorCode.INVALID_QR_CODE);
        }
        return BookingPass.of(views(List.of(a), false).get(0));
    }

    List<AppointmentView> views(List<Appointment> list, boolean withQr) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Organization> orgs = index(organizations.findAllById(ids(list, Appointment::getOrganizationId)), Organization::getId);
        Map<Long, Branch> brs = index(branches.findAllById(ids(list, Appointment::getBranchId)), Branch::getId);
        Map<Long, StaffProfile> st = index(staff.findAllById(ids(list, Appointment::getStaffId)), StaffProfile::getId);
        Map<Long, User> cust = index(users.findAllById(ids(list, Appointment::getCustomerId)), User::getId);
        java.util.Set<Long> paid = new java.util.HashSet<>(jdbc.queryForList(
                "SELECT appointment_id FROM payments WHERE status = 'CAPTURED' AND appointment_id IN (:ids)",
                Map.of("ids", ids(list, Appointment::getId)), Long.class));
        Map<Long, List<String>> seatsByAppointment = new java.util.HashMap<>();
        Map<Long, String> screenByShowtime = new java.util.HashMap<>();
        List<Long> showtimeIds = ids(list, Appointment::getShowtimeId);
        if (!showtimeIds.isEmpty()) {
            jdbc.query("SELECT appointment_id, seat_label FROM ticket_seats WHERE appointment_id IN (:ids) ORDER BY seat_label",
                    Map.of("ids", ids(list, Appointment::getId)), rs -> {
                        seatsByAppointment.computeIfAbsent(rs.getLong(1), k -> new java.util.ArrayList<>()).add(rs.getString(2));
                    });
            jdbc.query("SELECT st.id, sc.name FROM showtimes st JOIN cinema_screens sc ON sc.id = st.screen_id WHERE st.id IN (:ids)",
                    Map.of("ids", showtimeIds), rs -> {
                        screenByShowtime.put(rs.getLong(1), rs.getString(2));
                    });
        }
        java.util.Set<Long> reviewed = new java.util.HashSet<>(jdbc.queryForList(
                "SELECT appointment_id FROM reviews WHERE appointment_id IN (:ids)",
                Map.of("ids", ids(list, Appointment::getId)), Long.class));
        return list.stream().map(a -> {
            Organization o = orgs.get(a.getOrganizationId());
            Branch b = brs.get(a.getBranchId());
            StaffProfile s = a.getStaffId() == null ? null : st.get(a.getStaffId());
            User c = cust.get(a.getCustomerId());
            String address = b == null ? null : String.join(", ", java.util.stream.Stream.of(b.getAddressLine1(),
                    b.getLocality(), b.getCity()).filter(Objects::nonNull).toList());
            return new AppointmentView(a.getId(), a.getReference(), a.getStatus(), a.getStartsAt(), a.getEndsAt(),
                    b == null ? "Asia/Kolkata" : b.getTimezone(), a.getOrganizationId(), o == null ? null : o.getName(),
                    o == null ? null : o.getSlug(), a.getBranchId(), b == null ? null : b.getName(), address,
                    a.getServiceId(), a.getServiceName(), a.getStaffId(),
                    s == null ? null : String.join(" ", java.util.stream.Stream.of(s.getTitle(), s.getDisplayName())
                            .filter(Objects::nonNull).toList()),
                    c == null ? null : c.getFullName(), c == null ? null : c.getPhone(), a.getPatientName(), a.getServicePrice(),
                    a.getPlatformFee(), a.getTotalAmount(), a.getCustomerNote(), a.getCancelReason(), a.getCancelledBy(),
                    a.getRefundAmount(), a.getCheckedInAt(), a.getRescheduleCount(),
                    withQr && a.isActive() ? qr.payload(a.getReference()) : null, paid.contains(a.getId()), reviewed.contains(a.getId()), a.getCreatedAt(),
                    a.getShowtimeId(), a.getShowtimeId() == null ? null : screenByShowtime.get(a.getShowtimeId()),
                    seatsByAppointment.getOrDefault(a.getId(), List.of()), a.getPartySize(), a.getOfferTitle(),
                    a.getOfferPercent(), a.getBillAmount(), a.getDiscountAmount(), a.getCoverCredit());
        }).toList();
    }

    private static List<Long> ids(List<Appointment> list, Function<Appointment, Long> f) {
        return list.stream().map(f).filter(Objects::nonNull).distinct().toList();
    }

    private static <T> Map<Long, T> index(Iterable<T> items, Function<T, Long> id) {
        Map<Long, T> m = new HashMap<>();
        items.forEach(i -> m.put(id.apply(i), i));
        return m;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
