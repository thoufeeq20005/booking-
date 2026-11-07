package com.bookeasy.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bookeasy.appointment.Appointment;
import com.bookeasy.appointment.AppointmentChanged;
import com.bookeasy.appointment.AppointmentRepository;
import com.bookeasy.appointment.PaymentGate;
import com.bookeasy.appointment.SlotLocks;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.ratelimit.RateLimiter;
import com.bookeasy.organization.entity.ConfirmationMode;
import com.bookeasy.organization.repository.OrganizationRepository;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Razorpay checkout. A booking counts as paid only after the checkout signature is verified or a signed webhook
 * reports the capture; the browser's word alone never confirms anything. Refunds are queued in the same
 * transaction as the cancellation and sent by a retrying dispatcher.
 */
@Service
public class PaymentService implements PaymentGate {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    static final int MAX_REFUND_ATTEMPTS = 5;

    private final PaymentProperties properties;
    private final RazorpayClient razorpay;
    private final PaymentRepository payments;
    private final AppointmentRepository appointments;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final SlotLocks locks;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public PaymentService(PaymentProperties properties, RazorpayClient razorpay, PaymentRepository payments,
                          AppointmentRepository appointments, OrganizationRepository organizations, UserRepository users,
                          SlotLocks locks, JdbcTemplate jdbc, ObjectMapper json, ApplicationEventPublisher events,
                          TransactionTemplate tx, RateLimiter rateLimiter, Clock clock) {
        this.properties = properties;
        this.razorpay = razorpay;
        this.payments = payments;
        this.appointments = appointments;
        this.organizations = organizations;
        this.users = users;
        this.locks = locks;
        this.jdbc = jdbc;
        this.json = json;
        this.events = events;
        this.tx = tx;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Override
    public boolean requiresOnlinePayment(Appointment appointment) {
        return properties.online() && appointment.getTotalAmount().signum() > 0;
    }

    /** demo=true: no Razorpay; the page completes the payment with {@link #completeDemo}. */
    public record CheckoutOrder(String keyId, String orderId, long amountPaise, String currency, String reference,
                                String description, String customerName, String customerEmail, String customerPhone,
                                Instant expiresAt, boolean demo) {
    }

    /** Payment methods offered on the payment page (Razorpay Checkout method names). */
    public static final java.util.Set<String> METHODS = java.util.Set.of("upi", "card", "netbanking", "wallet");
    static final String DEMO_PREFIX = "demo_";

    // ----- checkout -----

    @Transactional
    public CheckoutOrder createOrder(long customerId, long appointmentId) {
        requireOnline();
        rateLimiter.hit("payorder:user:" + customerId, 30, Duration.ofHours(1));
        Appointment a = appointments.findByIdForUpdate(appointmentId).filter(x -> x.getCustomerId() == customerId)
                .orElseThrow(() -> ApiException.notFound("Booking"));
        if (a.getStatus() != Appointment.Status.PENDING_PAYMENT) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_REQUIRED);
        }
        Instant expires = a.getCreatedAt().plus(Duration.ofMinutes(properties.hold()));
        if (!expires.isAfter(clock.instant())) {
            throw new ApiException(ErrorCode.BOOKING_EXPIRED);
        }
        Payment p = payments.findByAppointmentIdOrderByIdDesc(a.getId()).stream()
                .filter(x -> x.getStatus() == Payment.Status.CREATED).findFirst()
                .orElseGet(() -> payments.save(new Payment(a.getId(), properties.demo()
                        ? DEMO_PREFIX + "order_" + randomId()
                        : razorpay.createOrder(paise(a.getTotalAmount()), a.getReference(),
                                Map.of("appointment_id", String.valueOf(a.getId()), "reference", a.getReference())),
                        paise(a.getTotalAmount()), clock.instant())));
        User u = users.findById(customerId).orElseThrow();
        String org = organizations.findById(a.getOrganizationId()).map(o -> o.getName()).orElse("BookEasy");
        return new CheckoutOrder(properties.razorpay().keyId(), p.getOrderId(), p.getAmountPaise(), "INR", a.getReference(),
                a.getServiceName() + " · " + org, u.getFullName(), u.getEmail(), u.getPhone(), expires,
                properties.demo());
    }

    /** Called by the browser after Razorpay Checkout succeeds. */
    @Transactional
    public void verifyCheckout(long customerId, String orderId, String paymentId, String signature) {
        requireEnabled();
        if (!matches(hmacHex(properties.razorpay().keySecret(), orderId + "|" + paymentId), signature)) {
            throw new ApiException(ErrorCode.PAYMENT_SIGNATURE_INVALID);
        }
        Payment p = payments.findByOrderIdForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Payment"));
        Appointment a = appointments.findById(p.getAppointmentId()).filter(x -> x.getCustomerId() == customerId)
                .orElseThrow(() -> ApiException.notFound("Payment"));
        capture(p, a.getId(), paymentId, null);
    }

    /**
     * Development demo gateway: marks the booking paid with the chosen method, exactly as a captured Razorpay payment
     * would (commission record, confirmation or approval, notifications). Refused unless demo mode is active.
     */
    @Transactional
    public void completeDemo(long customerId, long appointmentId, String method) {
        if (!properties.demo()) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "Demo payments are not available");
        }
        if (!METHODS.contains(method)) {
            throw ApiException.invalidField("method", "INVALID", "Choose a payment method");
        }
        Appointment a = appointments.findByIdForUpdate(appointmentId).filter(x -> x.getCustomerId() == customerId)
                .orElseThrow(() -> ApiException.notFound("Booking"));
        if (a.getStatus() != Appointment.Status.PENDING_PAYMENT) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_REQUIRED);
        }
        if (!a.getCreatedAt().plus(Duration.ofMinutes(properties.hold())).isAfter(clock.instant())) {
            throw new ApiException(ErrorCode.BOOKING_EXPIRED);
        }
        Payment p = payments.findByAppointmentIdOrderByIdDesc(a.getId()).stream()
                .filter(x -> x.getStatus() == Payment.Status.CREATED && x.getOrderId().startsWith(DEMO_PREFIX)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Payment"));
        capture(p, a.getId(), DEMO_PREFIX + "pay_" + randomId(), method);
    }

    /** Signed, de-duplicated Razorpay webhook. Returns false when the event was already processed. */
    @Transactional
    public boolean webhook(String rawBody, String signature, String eventId) {
        requireEnabled();
        if (!matches(hmacHex(properties.razorpay().webhookSecret(), rawBody), signature)) {
            throw new ApiException(ErrorCode.PAYMENT_SIGNATURE_INVALID);
        }
        JsonNode root;
        try {
            root = json.readTree(rawBody);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST);
        }
        String event = root.path("event").asText();
        String id = eventId != null && !eventId.isBlank() ? eventId : event + ":" + root.path("created_at").asText()
                + ":" + root.path("payload").toString().hashCode();
        try {
            jdbc.update("INSERT INTO webhook_events (event_id, event_type, received_at) VALUES (?, ?, ?)", id, event,
                    Timestamp.from(clock.instant()));
        } catch (DuplicateKeyException e) {
            return false;
        }
        JsonNode payment = root.path("payload").path("payment").path("entity");
        JsonNode refund = root.path("payload").path("refund").path("entity");
        switch (event) {
            case "payment.captured", "order.paid" -> payments.findByOrderIdForUpdate(payment.path("order_id").asText())
                    .ifPresent(p -> capture(p, p.getAppointmentId(), payment.path("id").asText(), payment.path("method").asText(null)));
            case "payment.failed" -> payments.findByOrderIdForUpdate(payment.path("order_id").asText())
                    .ifPresent(p -> p.failed(payment.path("id").asText(), clock.instant()));
            case "refund.processed" -> jdbc.update("UPDATE refunds SET status = 'PROCESSED', processed_at = ? WHERE razorpay_refund_id = ?",
                    Timestamp.from(clock.instant()), refund.path("id").asText());
            case "refund.failed" -> jdbc.update("UPDATE refunds SET status = 'FAILED', last_error = 'refund.failed webhook' "
                    + "WHERE razorpay_refund_id = ?", refund.path("id").asText());
            default -> log.debug("Ignoring Razorpay event {}", event);
        }
        return true;
    }

    /** Idempotent: a second report of the same capture changes nothing. */
    private void capture(Payment p, long appointmentId, String paymentId, String method) {
        if (p.getStatus() == Payment.Status.CAPTURED) {
            return;
        }
        Instant now = clock.instant();
        p.captured(paymentId, method, now);
        payments.flush();
        Appointment a = appointments.findByIdForUpdate(appointmentId).orElseThrow();
        if (a.getStatus() == Appointment.Status.PENDING_PAYMENT) {
            boolean approval = organizations.findById(a.getOrganizationId())
                    .map(o -> o.getConfirmationMode() == ConfirmationMode.APPROVAL).orElse(false);
            a.markPaid(approval, now);
            long gross = p.getAmountPaise();
            long fee = paise(a.getPlatformFee());
            jdbc.update("""
                    INSERT INTO commission_records (appointment_id, organization_id, gross_paise, commission_paise, payout_paise,
                                                    created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)""",
                    a.getId(), a.getOrganizationId(), gross, fee, gross - fee, Timestamp.from(now), Timestamp.from(now));
            events.publishEvent(new AppointmentChanged(a.getId(), AppointmentChanged.Kind.PAID));
        } else {
            // Paid after the hold expired or the booking was cancelled: give everything back.
            queueRefund(a, p, p.getAmountPaise());
        }
    }

    // ----- unpaid bookings expire -----

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void expireStaleScheduled() {
        expireStale();
    }

    /** Releases slots held by bookings that were never paid. Each booking in its own transaction. */
    public int expireStale() {
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(properties.hold()));
        int n = 0;
        for (Appointment stale : appointments.findByStatusAndCreatedAtBefore(Appointment.Status.PENDING_PAYMENT, cutoff)) {
            Boolean done = tx.execute(s -> appointments.findByIdForUpdate(stale.getId())
                    .filter(a -> a.getStatus() == Appointment.Status.PENDING_PAYMENT)
                    .map(a -> {
                        a.expire(clock.instant());
                        locks.release(a.getId());
                        events.publishEvent(new AppointmentChanged(a.getId(), AppointmentChanged.Kind.EXPIRED));
                        return true;
                    }).orElse(false));
            if (Boolean.TRUE.equals(done)) {
                n++;
            }
        }
        return n;
    }

    // ----- refunds -----

    /** Queues the refund in the cancelling transaction, so a cancellation can never lose its refund. */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCancelled(AppointmentChanged e) {
        if (e.kind() != AppointmentChanged.Kind.CANCELLED && e.kind() != AppointmentChanged.Kind.DECLINED) {
            return;
        }
        Appointment a = appointments.findById(e.appointmentId()).orElseThrow();
        if (a.getRefundAmount() == null || a.getRefundAmount().signum() <= 0) {
            return;
        }
        payments.findByAppointmentIdOrderByIdDesc(a.getId()).stream()
                .filter(p -> p.getStatus() == Payment.Status.CAPTURED).findFirst()
                .ifPresent(p -> queueRefund(a, p, Math.min(paise(a.getRefundAmount()), p.getAmountPaise())));
    }

    private void queueRefund(Appointment a, Payment p, long amountPaise) {
        Instant now = clock.instant();
        try {
            jdbc.update("INSERT INTO refunds (payment_id, appointment_id, amount_paise, status, created_at) VALUES (?, ?, ?, 'PENDING', ?)",
                    p.getId(), a.getId(), amountPaise, Timestamp.from(now));
        } catch (DuplicateKeyException ignored) {
            return; // already queued
        }
        // Send it as soon as this transaction commits; the scheduled dispatcher retries failures.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchRefunds();
            }
        });
        // The refund comes out of the business's share first; anything beyond that (business cancellations) out of the fee.
        jdbc.update("""
                UPDATE commission_records
                   SET refunded_paise = ?,
                       payout_paise = GREATEST(0, gross_paise - commission_paise - ?),
                       commission_paise = commission_paise - GREATEST(0, ? - (gross_paise - commission_paise)),
                       updated_at = ?
                 WHERE appointment_id = ? AND settlement_id IS NULL""",
                amountPaise, amountPaise, amountPaise, Timestamp.from(now), a.getId());
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT2M")
    public void dispatchRefundsScheduled() {
        dispatchRefunds();
    }

    /** Sends queued refunds to Razorpay; failures are retried up to {@value #MAX_REFUND_ATTEMPTS} times. */
    public int dispatchRefunds() {
        if (!properties.online()) {
            return 0;
        }
        Integer sent = tx.execute(s -> dispatchInTransaction());
        return sent == null ? 0 : sent;
    }

    private int dispatchInTransaction() {
        List<Map<String, Object>> due = jdbc.queryForList("""
                SELECT r.id, r.amount_paise, r.appointment_id, p.razorpay_payment_id FROM refunds r JOIN payments p ON p.id = r.payment_id
                WHERE r.status IN ('PENDING', 'FAILED') AND r.attempts < ? ORDER BY r.id LIMIT 50 FOR UPDATE SKIP LOCKED""",
                MAX_REFUND_ATTEMPTS);
        int sent = 0;
        for (Map<String, Object> r : due) {
            long id = ((Number) r.get("id")).longValue();
            String paymentId = (String) r.get("razorpay_payment_id");
            if (paymentId != null && paymentId.startsWith(DEMO_PREFIX)) {
                // Simulated payment: nothing to send to a gateway, the refund is settled immediately.
                jdbc.update("UPDATE refunds SET status = 'PROCESSED', razorpay_refund_id = ?, attempts = attempts + 1, last_error = NULL WHERE id = ?",
                        DEMO_PREFIX + "rfnd_" + id, id);
                sent++;
                continue;
            }
            try {
                String refundId = razorpay.refund(paymentId, ((Number) r.get("amount_paise")).longValue(),
                        Map.of("appointment_id", String.valueOf(r.get("appointment_id"))));
                jdbc.update("UPDATE refunds SET status = 'REQUESTED', razorpay_refund_id = ?, attempts = attempts + 1, last_error = NULL WHERE id = ?",
                        refundId, id);
                sent++;
            } catch (RuntimeException ex) {
                log.warn("Refund {} failed", id, ex);
                jdbc.update("UPDATE refunds SET status = 'FAILED', attempts = attempts + 1, last_error = ? WHERE id = ?",
                        truncate(ex.getMessage()), id);
            }
        }
        return sent;
    }

    // ----- receipt -----

    public record Receipt(String receiptNumber, Instant paidAt, String reference, String businessName, String customerName,
                          String serviceName, Instant appointmentAt, BigDecimal servicePrice, BigDecimal platformFee,
                          BigDecimal total, String paymentId, String method, BigDecimal refunded, String refundStatus) {
    }

    @Transactional(readOnly = true)
    public Receipt receipt(long customerId, long appointmentId) {
        Appointment a = appointments.findById(appointmentId).filter(x -> x.getCustomerId() == customerId)
                .orElseThrow(() -> ApiException.notFound("Booking"));
        Payment p = payments.findByAppointmentIdOrderByIdDesc(a.getId()).stream()
                .filter(x -> x.getStatus() == Payment.Status.CAPTURED).findFirst()
                .orElseThrow(() -> ApiException.notFound("Receipt"));
        List<Map<String, Object>> refund = jdbc.queryForList("SELECT amount_paise, status FROM refunds WHERE appointment_id = ?", a.getId());
        BigDecimal refunded = refund.isEmpty() ? null
                : BigDecimal.valueOf(((Number) refund.get(0).get("amount_paise")).longValue(), 2);
        return new Receipt("RCPT-" + a.getReference(), p.getCapturedAt(), a.getReference(),
                organizations.findById(a.getOrganizationId()).map(o -> o.getName()).orElse(""),
                users.findById(customerId).map(User::getFullName).orElse(""), a.getServiceName(), a.getStartsAt(),
                a.getServicePrice(), a.getPlatformFee(), a.getTotalAmount(), p.getPaymentId(), p.getMethod(), refunded,
                refund.isEmpty() ? null : (String) refund.get(0).get("status"));
    }

    // ----- helpers -----

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private static String randomId() {
        byte[] b = new byte[8];
        RANDOM.nextBytes(b);
        return java.util.HexFormat.of().formatHex(b);
    }

    private void requireOnline() {
        if (!properties.online()) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "Online payments are not configured");
        }
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "Online payments are not configured");
        }
    }

    static long paise(BigDecimal rupees) {
        return rupees.movePointRight(2).longValueExact();
    }

    static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean matches(String expected, String given) {
        return given != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                given.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private static String truncate(String s) {
        return s == null ? null : s.length() > 480 ? s.substring(0, 480) : s;
    }
}
