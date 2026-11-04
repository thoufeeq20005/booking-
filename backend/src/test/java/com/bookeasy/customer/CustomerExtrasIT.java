package com.bookeasy.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bookeasy.support.BookingTestBase;

class CustomerExtrasIT extends BookingTestBase {

    @Test
    void bookingForAFamilyMemberKeepsTheirName() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Person c = verifiedCustomer();
        long memberId = read(mvc.perform(jsonRequest(post("/api/v1/customer/family"), c.token(),
                Map.of("fullName", "Ravi Kumar", "relation", "CHILD", "dateOfBirth", "2015-06-01"))).andExpect(status().isOk()).andReturn())
                .path("data").path("id").asLong();
        Person other = verifiedCustomer();
        mvc.perform(jsonRequest(post("/api/v1/customer/appointments"), other.token(), Map.of("branchId", b.branchId(),
                "serviceId", b.serviceId(), "startsAt", b.at("09:00").toString(), "familyMemberId", memberId)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fieldErrors[0].field").value("familyMemberId"));
        mvc.perform(jsonRequest(post("/api/v1/customer/appointments"), c.token(), Map.of("branchId", b.branchId(),
                        "serviceId", b.serviceId(), "startsAt", b.at("09:00").toString(), "familyMemberId", memberId)))
                .andExpect(jsonPath("$.data.patientName").value("Ravi Kumar"));
        mvc.perform(jsonRequest(post("/api/v1/customer/family"), c.token(), Map.of("fullName", "Future", "relation", "CHILD",
                "dateOfBirth", "2999-01-01"))).andExpect(status().isBadRequest());
        mvc.perform(authed(delete("/api/v1/customer/family/" + memberId), other.token())).andExpect(status().isNotFound());
    }

    @Test
    void favouritesSavedPlacesAndSupport() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Person c = verifiedCustomer();
        mvc.perform(authed(put("/api/v1/customer/favourites/" + b.orgId()), c.token())).andExpect(status().isOk());
        mvc.perform(authed(put("/api/v1/customer/favourites/" + b.orgId()), c.token())).andExpect(status().isOk()); // idempotent
        mvc.perform(authed(get("/api/v1/customer/favourites"), c.token()))
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].city").value("Chennai"));
        long draft = createDraft(verifiedCustomer(), subcategoryId("gym"), "Draft Gym").path("summary").path("id").asLong();
        mvc.perform(authed(put("/api/v1/customer/favourites/" + draft), c.token())).andExpect(status().isNotFound());

        mvc.perform(jsonRequest(post("/api/v1/customer/saved-locations"), c.token(), Map.of("label", "Home", "latitude", 13.05,
                "longitude", 80.25))).andExpect(jsonPath("$.data[0].label").value("Home"));
        mvc.perform(jsonRequest(post("/api/v1/customer/saved-locations"), c.token(), Map.of("label", "London", "latitude", 51.5,
                "longitude", -0.12))).andExpect(status().isBadRequest());

        long reqId = read(mvc.perform(jsonRequest(post("/api/v1/support-requests"), c.token(),
                Map.of("subject", "Refund not received", "message", "It has been 5 days."))).andExpect(status().isOk()).andReturn())
                .path("data").path("id").asLong();
        Person admin = admin();
        mvc.perform(authed(get("/api/v1/admin/support-requests?status=OPEN"), admin.token()))
                .andExpect(jsonPath("$.data[?(@.id == " + reqId + ")].userEmail").value(c.email()));
        mvc.perform(jsonRequest(post("/api/v1/admin/support-requests/" + reqId), admin.token(),
                Map.of("status", "RESOLVED", "reply", "Refund was processed today."))).andExpect(jsonPath("$.data.status").value("RESOLVED"));
        mvc.perform(authed(get("/api/v1/support-requests"), c.token())).andExpect(jsonPath("$.data[0].adminReply").value("Refund was processed today."));
        mvc.perform(authed(get("/api/v1/account/notifications"), c.token())).andExpect(jsonPath("$.data[0].type").value("SUPPORT_REPLY"));
        mvc.perform(authed(get("/api/v1/admin/support-requests"), c.token())).andExpect(status().isForbidden());
    }

    @Test
    void retentionAnonymisesOldBookingsAndHidesThemFromHistory() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Person c = verifiedCustomer();
        long id = read(book(c, b, "11:00").andReturn()).path("data").path("id").asLong();
        Instant old = Instant.now().minus(3 * 366, ChronoUnit.DAYS);
        jdbc.update("UPDATE appointments SET starts_at = ?, ends_at = ?, blocked_until = ?, status = 'COMPLETED' WHERE id = ?",
                Timestamp.from(old), Timestamp.from(old.plusSeconds(1800)), Timestamp.from(old.plusSeconds(1800)), id);
        mvc.perform(authed(get("/api/v1/customer/appointments"), c.token()))
                .andExpect(jsonPath("$.data[?(@.id == " + id + ")]").isEmpty());

        mvc.perform(authed(post("/api/v1/admin/retention/run"), admin().token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentsAnonymised").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        assertThat(jdbc.queryForObject("SELECT customer_note FROM appointments WHERE id = ?", String.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT total_amount FROM appointments WHERE id = ?", java.math.BigDecimal.class, id))
                .isEqualByComparingTo("473.03");
    }
}
