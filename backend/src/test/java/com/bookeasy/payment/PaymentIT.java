package com.bookeasy.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import com.bookeasy.support.BookingTestBase;
import com.fasterxml.jackson.databind.JsonNode;

@TestPropertySource(properties = {
        "bookeasy.payments.razorpay.key-id=rzp_test_key",
        "bookeasy.payments.razorpay.key-secret=test_key_secret",
        "bookeasy.payments.razorpay.webhook-secret=test_webhook_secret",
        "bookeasy.payments.hold-minutes=15"})
@Import(PaymentIT.FakeRazorpayConfig.class)
class PaymentIT extends BookingTestBase {

    /** Records calls instead of talking to Razorpay. */
    static class FakeRazorpay implements RazorpayClient {
        final AtomicInteger seq = new AtomicInteger();
        final List<String> calls = new CopyOnWriteArrayList<>();
        volatile boolean failRefunds;

        @Override
        public String createOrder(long amountPaise, String receipt, Map<String, String> notes) {
            calls.add("order:" + amountPaise + ":" + receipt);
            return "order_" + seq.incrementAndGet();
        }

        @Override
        public String refund(String paymentId, long amountPaise, Map<String, String> notes) {
            if (failRefunds) {
                throw new RazorpayException("down", null);
            }
            calls.add("refund:" + paymentId + ":" + amountPaise);
            return "rfnd_" + seq.incrementAndGet();
        }

        @Override
        public String transfer(String paymentId, String linkedAccountId, long amountPaise) {
            calls.add("transfer:" + paymentId + ":" + amountPaise);
            return "trf_" + seq.incrementAndGet();
        }
    }

    @TestConfiguration
    static class FakeRazorpayConfig {
        @Bean
        @Primary
        FakeRazorpay fakeRazorpay() {
            return new FakeRazorpay();
        }
    }

    @Autowired
    FakeRazorpay razorpay;
    @Autowired
    PaymentService payments;
    @Autowired
    SettlementService settlements;

    private record Paid(Biz biz, Person customer, long appointmentId, String orderId, String paymentId) {
    }

    private JsonNode order(Person c, long id) throws Exception {
        return read(mvc.perform(authed(post("/api/v1/customer/appointments/" + id + "/payment-order"), c.token()))
                .andExpect(status().isOk()).andReturn()).path("data");
    }

    private Paid bookAndPay(Biz b, String time) throws Exception {
        Person c = verifiedCustomer();
        JsonNode a = read(book(c, b, time).andExpect(status().isOk()).andReturn()).path("data");
        long id = a.path("id").asLong();
        String orderId = order(c, id).path("orderId").asText();
        String paymentId = "pay_" + System.nanoTime();
        mvc.perform(jsonRequest(post("/api/v1/customer/payments/verify"), c.token(), Map.of("orderId", orderId,
                "paymentId", paymentId, "signature", PaymentService.hmacHex("test_key_secret", orderId + "|" + paymentId))))
                .andExpect(status().isOk());
        return new Paid(b, c, id, orderId, paymentId);
    }

    private org.springframework.test.web.servlet.ResultActions webhook(String body, String eventId, String secret) throws Exception {
        return mvc.perform(post("/api/v1/public/webhooks/razorpay").contentType("application/json").content(body)
                .header("X-Razorpay-Event-Id", eventId)
                .header("X-Razorpay-Signature", PaymentService.hmacHex(secret, body)));
    }

    private Map<String, Object> commission(long appointmentId) {
        return jdbc.queryForMap("SELECT * FROM commission_records WHERE appointment_id = ?", appointmentId);
    }

    @Test
    void checkoutSignatureConfirmsTheBookingAndRecordsCommission() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Person c = verifiedCustomer();
        JsonNode a = read(book(c, b, "09:00").andReturn()).path("data");
        assertThat(a.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        long id = a.path("id").asLong();

        JsonNode o = order(c, id);
        assertThat(o.path("amountPaise").asLong()).isEqualTo(47303);
        assertThat(o.path("keyId").asText()).isEqualTo("rzp_test_key");
        assertThat(order(c, id).path("orderId").asText()).as("order reused").isEqualTo(o.path("orderId").asText());
        assertThat(razorpay.calls).filteredOn(s -> s.endsWith(":" + a.path("reference").asText())).hasSize(1);
        mvc.perform(authed(post("/api/v1/customer/appointments/" + id + "/payment-order"), verifiedCustomer().token()))
                .andExpect(status().isNotFound());

        String orderId = o.path("orderId").asText();
        mvc.perform(jsonRequest(post("/api/v1/customer/payments/verify"), c.token(), Map.of("orderId", orderId,
                "paymentId", "pay_1", "signature", "deadbeef"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_SIGNATURE_INVALID"));
        String sig = PaymentService.hmacHex("test_key_secret", orderId + "|pay_1");
        for (int i = 0; i < 2; i++) {
            mvc.perform(jsonRequest(post("/api/v1/customer/payments/verify"), c.token(),
                    Map.of("orderId", orderId, "paymentId", "pay_1", "signature", sig))).andExpect(status().isOk());
        }
        mvc.perform(authed(get("/api/v1/customer/appointments/" + id), c.token())).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        Map<String, Object> cr = commission(id);
        assertThat(((Number) cr.get("gross_paise")).longValue()).isEqualTo(47303);
        assertThat(((Number) cr.get("commission_paise")).longValue()).isEqualTo(2253);
        assertThat(((Number) cr.get("payout_paise")).longValue()).isEqualTo(45050);

        mvc.perform(authed(get("/api/v1/customer/appointments/" + id + "/receipt"), c.token()))
                .andExpect(jsonPath("$.data.receiptNumber").value("RCPT-" + a.path("reference").asText()))
                .andExpect(jsonPath("$.data.total").value(473.03))
                .andExpect(jsonPath("$.data.paymentId").value("pay_1"));
    }

    @Test
    void webhooksAreVerifiedAndProcessedOnce() throws Exception {
        Biz b = approvedClinic("APPROVAL");
        Person c = verifiedCustomer();
        long id = read(book(c, b, "09:30").andReturn()).path("data").path("id").asLong();
        String orderId = order(c, id).path("orderId").asText();
        String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_wh1\",\"order_id\":\""
                + orderId + "\",\"method\":\"upi\"}}}}";

        webhook(body, "evt_1", "wrong_secret").andExpect(status().isBadRequest());
        webhook(body, "evt_1", "test_webhook_secret").andExpect(status().isOk());
        webhook(body, "evt_1", "test_webhook_secret").andExpect(status().isOk()); // replay ignored
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commission_records WHERE appointment_id = ?", Integer.class, id)).isEqualTo(1);
        mvc.perform(authed(get("/api/v1/customer/appointments/" + id), c.token()))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));
        assertThat(jdbc.queryForObject("SELECT method FROM payments WHERE razorpay_order_id = ?", String.class, orderId)).isEqualTo("upi");
    }

    @Test
    void unpaidBookingsExpireAndLatePaymentsAreRefunded() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Person c = verifiedCustomer();
        long id = read(book(c, b, "10:00").andReturn()).path("data").path("id").asLong();
        String orderId = order(c, id).path("orderId").asText();

        clock.advance(Duration.ofMinutes(16));
        assertThat(payments.expireStale()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM appointments WHERE id = ?", String.class, id)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointment_slot_locks WHERE appointment_id = ?", Integer.class, id)).isZero();

        String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_late\",\"order_id\":\"" + orderId + "\"}}}}";
        webhook(body, "evt_late", "test_webhook_secret").andExpect(status().isOk());
        assertThat(razorpay.calls).contains("refund:pay_late:47303");
        assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE appointment_id = ?", String.class, id)).isEqualTo("REQUESTED");
        clock.reset();
    }

    @Test
    void cancellationsQueueRefundsThatRetryAndAdjustCommission() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Paid p = bookAndPay(b, "09:00");
        razorpay.failRefunds = true;
        mvc.perform(jsonRequest(post("/api/v1/customer/appointments/" + p.appointmentId() + "/cancel"), p.customer().token(),
                Map.of("reason", "Plans changed"))).andExpect(jsonPath("$.data.refundAmount").value(450.50));
        assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE appointment_id = ?", String.class, p.appointmentId())).isEqualTo("FAILED");
        razorpay.failRefunds = false;
        payments.dispatchRefunds();
        assertThat(razorpay.calls).contains("refund:" + p.paymentId() + ":45050");
        Map<String, Object> cr = commission(p.appointmentId());
        assertThat(((Number) cr.get("payout_paise")).longValue()).isZero();
        assertThat(((Number) cr.get("commission_paise")).longValue()).isEqualTo(2253); // fee kept on customer cancellations

        // A business cancellation refunds everything, including the platform fee.
        Paid q = bookAndPay(b, "09:30");
        mvc.perform(jsonRequest(post("/api/v1/owner/organizations/" + b.orgId() + "/appointments/" + q.appointmentId() + "/cancel"),
                b.owner().token(), Map.of("reason", "Doctor unwell"))).andExpect(status().isOk());
        assertThat(razorpay.calls).contains("refund:" + q.paymentId() + ":47303");
        assertThat(((Number) commission(q.appointmentId()).get("commission_paise")).longValue()).isZero();

        // Refund webhook marks it processed.
        String refundId = jdbc.queryForObject("SELECT razorpay_refund_id FROM refunds WHERE appointment_id = ?", String.class, q.appointmentId());
        webhook("{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"" + refundId + "\"}}}}", "evt_r1",
                "test_webhook_secret").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE appointment_id = ?", String.class, q.appointmentId())).isEqualTo("PROCESSED");
    }

    @Test
    void completedBookingsAreSettledAndPaidOutManually() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Paid p = bookAndPay(b, "11:00");
        clock.advance(Duration.ofDays(8));
        String owner = login(b.owner().email(), PASSWORD).accessToken();
        mvc.perform(authed(post("/api/v1/owner/organizations/" + b.orgId() + "/appointments/" + p.appointmentId() + "/complete"), owner))
                .andExpect(status().isOk());
        mvc.perform(authed(get("/api/v1/owner/organizations/" + b.orgId() + "/earnings"), owner))
                .andExpect(jsonPath("$.data.pendingPayoutPaise").value(45050));

        assertThat(settlements.settle()).isGreaterThanOrEqualTo(1);
        Person admin = admin();
        JsonNode list = read(mvc.perform(authed(get("/api/v1/admin/settlements?status=PENDING_MANUAL"), admin.token())).andReturn()).path("data");
        JsonNode mine = null;
        for (JsonNode s : list) {
            if (s.path("organizationId").asLong() == b.orgId()) {
                mine = s;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.path("amountPaise").asLong()).isEqualTo(45050);
        mvc.perform(jsonRequest(post("/api/v1/admin/settlements/" + mine.path("id").asLong() + "/paid"), admin.token(),
                Map.of("reference", "NEFT-UTR-123"))).andExpect(jsonPath("$.data.status").value("PAID"));
        mvc.perform(authed(get("/api/v1/owner/organizations/" + b.orgId() + "/earnings"), owner))
                .andExpect(jsonPath("$.data.pendingPayoutPaise").value(0))
                .andExpect(jsonPath("$.data.settledPaise").value(45050));
        clock.reset();
    }
}
