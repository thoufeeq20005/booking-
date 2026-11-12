package com.bookeasy.notification;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.bookeasy.appointment.Appointment;
import com.bookeasy.appointment.AppointmentChanged;
import com.bookeasy.appointment.AppointmentRepository;
import com.bookeasy.organization.repository.OrganizationRepository;
import com.bookeasy.staff.StaffProfileRepository;
import com.bookeasy.user.repository.UserRepository;

/** Turns booking events into notifications for the customer or the business, and sends appointment reminders. */
@Component
public class BookingNotifications {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final NotificationService notifications;
    private final AppointmentRepository appointments;
    private final OrganizationRepository organizations;
    private final StaffProfileRepository staff;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private org.springframework.beans.factory.ObjectProvider<com.bookeasy.appointment.BookingService> bookingService;
    private org.springframework.context.MessageSource messages;
    private String frontendUrl;

    /** Ticket emails need the booking view, the label texts and the site address (set by Spring after construction). */
    @org.springframework.beans.factory.annotation.Autowired
    void ticketSupport(org.springframework.beans.factory.ObjectProvider<com.bookeasy.appointment.BookingService> bookingService,
                       org.springframework.context.MessageSource messages, com.bookeasy.common.config.BookEasyProperties properties) {
        this.bookingService = bookingService;
        this.messages = messages;
        this.frontendUrl = properties.frontendUrl();
    }

    public BookingNotifications(NotificationService notifications, AppointmentRepository appointments,
                                OrganizationRepository organizations, StaffProfileRepository staff, UserRepository users,
                                JdbcTemplate jdbc, TransactionTemplate tx, Clock clock) {
        this.notifications = notifications;
        this.appointments = appointments;
        this.organizations = organizations;
        this.staff = staff;
        this.users = users;
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
    }

    /** Same transaction as the booking change: the notification exists exactly when the change does. */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(AppointmentChanged e) {
        Appointment a = appointments.findById(e.appointmentId()).orElse(null);
        if (a == null) {
            return;
        }
        String mine = "/customer/bookings/" + a.getId();
        switch (e.kind()) {
            case BOOKED, PAID -> {
                toCustomer(a, a.getStatus() == Appointment.Status.PENDING_APPROVAL ? "BOOKING_REQUESTED" : "BOOKING_CONFIRMED", mine);
                toBusiness(a, "BOOKING_NEW");
            }
            case CONFIRMED -> toCustomer(a, "BOOKING_APPROVED", mine);
            case DECLINED -> toCustomer(a, "BOOKING_DECLINED", mine);
            case CANCELLED -> {
                if (a.getCancelledBy() == Appointment.Party.CUSTOMER) {
                    toBusiness(a, "BOOKING_CANCELLED_BY_CUSTOMER");
                } else {
                    toCustomer(a, "BOOKING_CANCELLED_BY_BUSINESS", mine);
                }
            }
            case RESCHEDULED -> toBusiness(a, "BOOKING_RESCHEDULED");
            case EXPIRED -> toCustomer(a, "BOOKING_EXPIRED", mine);
            case COMPLETED -> toCustomer(a, "BOOKING_COMPLETED", mine);
            default -> { }
        }
    }

    private void toCustomer(Appointment a, String type, String link) {
        if (type.equals("BOOKING_CONFIRMED") && a.getStatus() == Appointment.Status.CONFIRMED) {
            // The e-ticket: details, amounts and the booking pass QR, in the customer's language.
            com.bookeasy.appointment.AppointmentView view = bookingService.getObject().view(a, true);
            notifications.notify(a.getCustomerId(), type, args(a, a.getCustomerId()), link,
                    locale -> TicketEmail.render(view, locale, messages, frontendUrl, link));
            return;
        }
        notifications.notify(a.getCustomerId(), type, args(a, a.getCustomerId()), link);
    }

    /** Owners of the business and the staff member serving the booking. */
    private void toBusiness(Appointment a, String type) {
        Set<Long> recipients = new LinkedHashSet<>(jdbc.queryForList(
                "SELECT user_id FROM organization_owners WHERE organization_id = ?", Long.class, a.getOrganizationId()));
        Long staffUser = a.getStaffId() == null ? null : staff.findById(a.getStaffId()).map(s -> s.getUserId()).orElse(null);
        for (Long owner : recipients) {
            notifications.notify(owner, type, args(a, owner), "/owner/appointments");
        }
        if (staffUser != null && !recipients.contains(staffUser)) {
            notifications.notify(staffUser, type, args(a, staffUser), "/staff");
        }
    }

    /** {0} service, {1} business, {2} when (recipient's language, India time), {3} reason, {4} customer name. */
    private Object[] args(Appointment a, long recipient) {
        Locale locale = Locale.forLanguageTag(users.findById(recipient).map(u -> u.getPreferredLanguage()).orElse("en") + "-IN");
        String when = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(IST)
                .format(a.getStartsAt());
        String business = organizations.findById(a.getOrganizationId()).map(o -> o.getName()).orElse("");
        String customer = users.findById(a.getCustomerId()).map(u -> u.getFullName()).orElse("");
        return new Object[]{a.getServiceName(), business, when, a.getCancelReason() == null ? "" : a.getCancelReason(), customer};
    }

    // ----- reminders -----

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void remindScheduled() {
        sendReminders();
    }

    /**
     * For each configured offset (minutes before the start), reminds confirmed bookings whose reminder time has come
     * and that haven't started. The reminders table's primary key makes each reminder go out once.
     */
    public int sendReminders() {
        Instant now = clock.instant();
        List<Integer> offsets = List.of(1440, 60);
        try {
            offsets = java.util.Arrays.stream(jdbc.queryForObject(
                    "SELECT setting_value FROM platform_settings WHERE setting_key = 'reminder.default_offsets_minutes'", String.class)
                    .split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt).toList();
        } catch (RuntimeException ignored) {
            // keep the defaults
        }
        final List<Integer> windows = offsets;
        int max = windows.stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Long> due = jdbc.queryForList("""
                SELECT id FROM appointments WHERE status = 'CONFIRMED' AND starts_at > ? AND starts_at <= ?""",
                Long.class, Timestamp.from(now), Timestamp.from(now.plus(Duration.ofMinutes(max))));
        int sent = 0;
        for (Long id : due) {
            for (int offset : windows) {
                Boolean ok = tx.execute(s -> {
                    Appointment a = appointments.findById(id).orElse(null);
                    if (a == null || a.getStatus() != Appointment.Status.CONFIRMED
                            || a.getStartsAt().minus(Duration.ofMinutes(offset)).isAfter(now)) {
                        return false;
                    }
                    // When a closer reminder is also due (e.g. booked 30 minutes ahead), only that one is sent.
                    boolean closerDue = windows.stream().anyMatch(o -> o < offset
                            && !a.getStartsAt().minus(Duration.ofMinutes(o)).isAfter(now));
                    try {
                        jdbc.update("INSERT INTO appointment_reminders (appointment_id, offset_minutes, sent_at) VALUES (?, ?, ?)",
                                id, offset, Timestamp.from(now));
                    } catch (DuplicateKeyException e) {
                        return false;
                    }
                    if (closerDue) {
                        return false;
                    }
                    notifications.notify(a.getCustomerId(), "REMINDER", args(a, a.getCustomerId()), "/customer/bookings/" + id);
                    return true;
                });
                if (Boolean.TRUE.equals(ok)) {
                    sent++;
                }
            }
        }
        return sent;
    }
}
