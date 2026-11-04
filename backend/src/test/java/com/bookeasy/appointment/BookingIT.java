package com.bookeasy.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.bookeasy.support.OrganizationTestBase;
import com.fasterxml.jackson.databind.JsonNode;

class BookingIT extends OrganizationTestBase {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private record Biz(Person owner, long orgId, long branchId, long serviceId, long staffId, LocalDate monday) {
        String slots() {
            return "/api/v1/public/branches/" + branchId + "/services/" + serviceId + "/slots?date=" + monday;
        }

        Instant at(String time) {
            return ZonedDateTime.of(monday, LocalTime.parse(time), IST).toInstant();
        }
    }

    /** Approved clinic; the owner is the only staff member, Mondays 09:00–12:00; 30-minute service at ₹450.50. */
    private Biz approvedClinic(String confirmationMode) throws Exception {
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

    private org.springframework.test.web.servlet.ResultActions book(Person customer, Biz b, String time) throws Exception {
        return mvc.perform(jsonRequest(post("/api/v1/customer/appointments"), customer.token(), Map.of(
                "branchId", b.branchId(), "serviceId", b.serviceId(), "startsAt", b.at(time).toString(), "note", "First visit")));
    }

    private List<String> freeTimes(Biz b) throws Exception {
        List<String> out = new ArrayList<>();
        read(mvc.perform(get(b.slots())).andReturn()).path("data").path("slots").forEach(s -> out.add(s.path("time").asText()));
        return out;
    }

    @Test
    void bookingTakesTheSlotChargesThePlatformFeeAndCanBeCancelledWithRefund() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Person asha = verifiedCustomer();
        JsonNode a = read(book(asha, b, "09:00").andExpect(status().isOk()).andReturn()).path("data");
        assertThat(a.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(a.path("reference").asText()).matches("BE[A-Z2-9]{8}");
        assertThat(a.path("qrPayload").asText()).startsWith("BOOKEASY:" + a.path("reference").asText() + ":");
        assertThat(a.path("staffName").asText()).isEqualTo("Dr. Owner");
        assertThat(a.path("servicePrice").decimalValue()).isEqualByComparingTo("450.50");
        assertThat(a.path("platformFee").decimalValue()).isEqualByComparingTo("22.53"); // 5% of 450.50, half-up
        assertThat(a.path("totalAmount").decimalValue()).isEqualByComparingTo("473.03");
        assertThat(freeTimes(b)).doesNotContain("09:00").contains("09:30");

        book(verifiedCustomer(), b, "09:00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SLOT_UNAVAILABLE"));
        book(verifiedCustomer(), b, "09:10").andExpect(jsonPath("$.error.code").value("SLOT_UNAVAILABLE")); // not a real slot

        long id = a.path("id").asLong();
        mvc.perform(authed(get("/api/v1/customer/appointments/" + id + "/cancel-preview"), asha.token()))
                .andExpect(jsonPath("$.data.refundPercent").value(100))
                .andExpect(jsonPath("$.data.refundAmount").value(450.50));
        mvc.perform(authed(get("/api/v1/customer/appointments/" + id), verifiedCustomer().token())).andExpect(status().isNotFound());
        mvc.perform(jsonRequest(post("/api/v1/customer/appointments/" + id + "/cancel"), asha.token(), Map.of("reason", "Travelling")))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.refundAmount").value(450.50))
                .andExpect(jsonPath("$.data.qrPayload").doesNotExist());
        assertThat(freeTimes(b)).contains("09:00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointment_slot_locks WHERE appointment_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void concurrentBookingsOfTheSameSlotLetExactlyOneThrough() throws Exception {
        Biz b = approvedClinic("INSTANT");
        int threads = 6;
        List<Person> customers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            customers.add(verifiedCustomer());
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> results = new ArrayList<>();
        for (Person c : customers) {
            Callable<Integer> task = () -> {
                start.await();
                return book(c, b, "10:00").andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();
        assertThat(statuses).containsOnly(200, 409);
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE organization_id = ? AND status = 'CONFIRMED'",
                Integer.class, b.orgId())).isEqualTo(1);
    }

    @Test
    void unverifiedCustomersCannotBookAndRescheduleMovesTheSlot() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Session unverified = register(uniqueEmail());
        mvc.perform(jsonRequest(post("/api/v1/customer/appointments"), unverified.accessToken(), Map.of("branchId", b.branchId(),
                "serviceId", b.serviceId(), "startsAt", b.at("09:00").toString())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("VERIFICATION_REQUIRED"));

        Person c = verifiedCustomer();
        long id = read(book(c, b, "09:00").andReturn()).path("data").path("id").asLong();
        String url = "/api/v1/customer/appointments/" + id + "/reschedule";
        mvc.perform(jsonRequest(post(url), c.token(), Map.of("startsAt", b.at("11:00").toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rescheduleCount").value(1));
        assertThat(freeTimes(b)).contains("09:00").doesNotContain("11:00");
        mvc.perform(jsonRequest(post(url), c.token(), Map.of("startsAt", b.at("10:00").toString()))).andExpect(status().isOk());
        mvc.perform(jsonRequest(post(url), c.token(), Map.of("startsAt", b.at("09:30").toString())))
                .andExpect(jsonPath("$.error.code").value("RESCHEDULE_LIMIT_REACHED"));
    }

    @Test
    void approvalModeQrCheckInAndCompletion() throws Exception {
        Biz b = approvedClinic("APPROVAL");
        Person c = verifiedCustomer();
        JsonNode a = read(book(c, b, "09:30").andExpect(status().isOk()).andReturn()).path("data");
        assertThat(a.path("status").asText()).isEqualTo("PENDING_APPROVAL");
        String base = "/api/v1/owner/organizations/" + b.orgId();
        long id = a.path("id").asLong();

        mvc.perform(authed(get(base + "/appointments?date=" + b.monday()), b.owner().token()))
                .andExpect(jsonPath("$.data[0].customerName").value("Asha Kumar"))
                .andExpect(jsonPath("$.data[0].qrPayload").doesNotExist());
        Person stranger = verifiedCustomer();
        createDraft(stranger, subcategoryId("gym"), "Other");
        mvc.perform(authed(post(base + "/appointments/" + id + "/confirm"), stranger.token())).andExpect(status().isNotFound());

        mvc.perform(authed(post(base + "/appointments/" + id + "/decline"), b.owner().token()))
                .andExpect(status().isBadRequest());
        // The owner is also the staff member here: staff actions work through /staff too.
        mvc.perform(authed(post("/api/v1/staff/appointments/" + id + "/confirm"), b.owner().token()))
                .andExpect(status().isForbidden()); // owner-as-staff has no CAN_CONFIRM permission
        mvc.perform(authed(post(base + "/appointments/" + id + "/confirm"), b.owner().token()))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        String code = a.path("qrPayload").asText();
        mvc.perform(jsonRequest(post(base + "/check-in"), b.owner().token(), Map.of("code", code.substring(0, code.length() - 1) + "x")))
                .andExpect(jsonPath("$.error.code").value("INVALID_QR_CODE"));
        mvc.perform(jsonRequest(post(base + "/check-in"), b.owner().token(), Map.of("code", code)))
                .andExpect(jsonPath("$.data.checkedInAt").exists());
        mvc.perform(authed(post(base + "/appointments/" + id + "/complete"), b.owner().token()))
                .andExpect(jsonPath("$.error.code").value("BOOKING_NOT_CHANGEABLE")); // not started yet

        clock.advance(Duration.ofDays(8));
        String ownerToken = login(b.owner().email(), PASSWORD).accessToken();
        mvc.perform(authed(post(base + "/appointments/" + id + "/complete"), ownerToken))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mvc.perform(authed(get("/api/v1/staff/appointments?date=" + b.monday()), ownerToken))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"));
    }
}
