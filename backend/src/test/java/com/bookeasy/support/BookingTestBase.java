package com.bookeasy.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

/** An approved clinic ready to take bookings, plus a helper to book it. */
public abstract class BookingTestBase extends OrganizationTestBase {

    protected static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    protected record Biz(Person owner, long orgId, long branchId, long serviceId, long staffId, LocalDate monday) {
        public String slots() {
            return "/api/v1/public/branches/" + branchId + "/services/" + serviceId + "/slots?date=" + monday;
        }

        public Instant at(String time) {
            return ZonedDateTime.of(monday, LocalTime.parse(time), IST).toInstant();
        }
    }

    /** Approved clinic; the owner is the only staff member, Mondays 09:00–12:00; 30-minute service at ₹450.50. */
    protected Biz approvedClinic(String confirmationMode) throws Exception {
        Person owner = verifiedCustomer();
        Map<String, Object> body = orgBody(subcategoryId("general-clinic"), "Clinic " + System.nanoTime());
        body.put("confirmationMode", confirmationMode);
        long orgId = read(mvc.perform(jsonRequest(post("/api/v1/business/organizations"), owner.token(), body))
                .andReturn()).path("data").path("summary").path("id").asLong();
        String base = "/api/v1/owner/organizations/" + orgId;
        long branchId = addBranch(owner, orgId);
        long serviceId = read(mvc.perform(jsonRequest(post(base + "/services"), owner.token(), Map.of("name", "Consultation",
                "durationMinutes", 30, "price", "450.50", "slotIntervalMinutes", 30, "minNoticeMinutes", 0,
                "branches", List.of(Map.of("branchId", branchId))))).andReturn()).path("data").path("id").asLong();
        long staffId = read(mvc.perform(jsonRequest(post(base + "/staff/self"), owner.token(), Map.of("displayName", "Dr. Owner",
                "branchIds", List.of(branchId), "serviceIds", List.of(serviceId)))).andReturn()).path("data").path("id").asLong();
        mvc.perform(jsonRequest(put(base + "/staff/" + staffId + "/availability"), owner.token(), Map.of("windows", List.of(
                Map.of("branchId", branchId, "dayOfWeek", 1, "startTime", "09:00", "endTime", "12:00"))))).andExpect(status().isOk());
        uploadMandatoryDocuments(owner, orgId);
        mvc.perform(authed(post(base + "/submit"), owner.token())).andExpect(status().isOk());
        mvc.perform(authed(post("/api/v1/admin/organizations/" + orgId + "/approve"), admin().token())).andExpect(status().isOk());
        return new Biz(owner, orgId, branchId, serviceId, staffId,
                LocalDate.now(IST).with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
    }

    protected org.springframework.test.web.servlet.ResultActions book(Person customer, Biz b, String time) throws Exception {
        return mvc.perform(jsonRequest(post("/api/v1/customer/appointments"), customer.token(), Map.of(
                "branchId", b.branchId(), "serviceId", b.serviceId(), "startsAt", b.at(time).toString(), "note", "First visit")));
    }

}
