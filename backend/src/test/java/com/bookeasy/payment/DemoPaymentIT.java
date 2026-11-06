package com.bookeasy.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.bookeasy.support.BookingTestBase;
import com.fasterxml.jackson.databind.JsonNode;

/** The development demo gateway: no Razorpay keys, demo mode on. */
@TestPropertySource(properties = {"bookeasy.payments.demo-mode=true", "bookeasy.payments.hold-minutes=15"})
class DemoPaymentIT extends BookingTestBase {

    @Autowired
    PaymentService payments;

    private long bookPending(Person c, Biz b, String time) throws Exception {
        JsonNode a = read(book(c, b, time).andExpect(status().isOk()).andReturn()).path("data");
        assertThat(a.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        return a.path("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions demoPay(Person c, long id, String method) throws Exception {
        return mvc.perform(jsonRequest(post("/api/v1/customer/appointments/" + id + "/demo-payment"), c.token(),
                Map.of("method", method)));
    }

    @Test
    void choosingAMethodAndPayingConfirmsTheBooking() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Person c = verifiedCustomer();
        long id = bookPending(c, b, "09:00");

        JsonNode order = read(mvc.perform(authed(post("/api/v1/customer/appointments/" + id + "/payment-order"), c.token()))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(order.path("demo").asBoolean()).isTrue();
        assertThat(order.path("orderId").asText()).startsWith("demo_order_");
        assertThat(order.path("amountPaise").asLong()).isEqualTo(47303);

        demoPay(c, id, "bitcoin").andExpect(status().isBadRequest());
        demoPay(verifiedCustomer(), id, "upi").andExpect(status().isNotFound()); // someone else's booking
        demoPay(c, id, "upi").andExpect(status().isOk());
        demoPay(c, id, "upi").andExpect(jsonPath("$.error.code").value("PAYMENT_NOT_REQUIRED")); // paid once only

        mvc.perform(authed(get("/api/v1/customer/appointments/" + id), c.token()))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        assertThat(jdbc.queryForObject("SELECT method FROM payments WHERE appointment_id = ?", String.class, id)).isEqualTo("upi");
        assertThat(jdbc.queryForObject("SELECT commission_paise FROM commission_records WHERE appointment_id = ?",
                Long.class, id)).isEqualTo(2253L);
        mvc.perform(authed(get("/api/v1/customer/appointments/" + id + "/receipt"), c.token()))
                .andExpect(jsonPath("$.data.method").value("upi"));
    }

    @Test
    void cancellingADemoPaymentRefundsItLocally() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Person c = verifiedCustomer();
        long id = bookPending(c, b, "09:30");
        mvc.perform(authed(post("/api/v1/customer/appointments/" + id + "/payment-order"), c.token())).andExpect(status().isOk());
        demoPay(c, id, "card").andExpect(status().isOk());

        mvc.perform(jsonRequest(post("/api/v1/customer/appointments/" + id + "/cancel"), c.token(), Map.of("reason", "Plans changed")))
                .andExpect(status().isOk());
        payments.dispatchRefunds();
        assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE appointment_id = ?", String.class, id)).isEqualTo("PROCESSED");
    }

    @Test
    void anExpiredHoldCannotBePaid() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Person c = verifiedCustomer();
        long id = bookPending(c, b, "10:00");
        mvc.perform(authed(post("/api/v1/customer/appointments/" + id + "/payment-order"), c.token())).andExpect(status().isOk());
        // Age the booking past its 15-minute hold (moving the clock would also expire the login).
        jdbc.update("UPDATE appointments SET created_at = created_at - INTERVAL 16 MINUTE WHERE id = ?", id);
        demoPay(c, id, "netbanking").andExpect(jsonPath("$.error.code").value("BOOKING_EXPIRED"));
    }
}
