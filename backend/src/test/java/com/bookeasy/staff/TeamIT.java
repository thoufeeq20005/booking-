package com.bookeasy.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bookeasy.support.OrganizationTestBase;
import com.fasterxml.jackson.databind.JsonNode;

class TeamIT extends OrganizationTestBase {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private record Setup(Person owner, long orgId, long branchId, long serviceId) {
        String base() {
            return "/api/v1/owner/organizations/" + orgId;
        }
    }

    private Map<String, Object> service(long branchId, String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("durationMinutes", 30);
        m.put("price", "500.00");
        m.put("slotIntervalMinutes", 30);
        m.put("minNoticeMinutes", 0);
        m.put("branches", List.of(Map.of("branchId", branchId, "priceOverride", "450.50")));
        return m;
    }

    private static Map<String, Object> profile(String name, long branchId, long serviceId, String... perms) {
        return Map.of("displayName", name, "title", "Dr.", "specialisation", "Dentist",
                "permissions", List.of(perms), "branchIds", List.of(branchId), "serviceIds", List.of(serviceId));
    }

    private Setup setup() throws Exception {
        Person owner = verifiedCustomer();
        long orgId = createDraft(owner, subcategoryId("dental-clinic"), "Team Dental").path("summary").path("id").asLong();
        long branchId = addBranch(owner, orgId); // Mon–Sat 09:00–18:00, lunch 13:00–14:00
        long serviceId = read(mvc.perform(jsonRequest(post("/api/v1/owner/organizations/" + orgId + "/services"),
                owner.token(), service(branchId, "Check-up"))).andExpect(status().isOk()).andReturn())
                .path("data").path("id").asLong();
        return new Setup(owner, orgId, branchId, serviceId);
    }

    private static LocalDate nextMonday() {
        return LocalDate.now(IST).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    @Test
    void servicesAreValidatedAndScopedToTheOwner() throws Exception {
        Setup s = setup();
        mvc.perform(authed(get(s.base() + "/services"), s.owner().token()))
                .andExpect(jsonPath("$.data[0].price").value(500.00))
                .andExpect(jsonPath("$.data[0].branches[0].priceOverride").value(450.50));

        Map<String, Object> bad = service(s.branchId(), "Bad");
        bad.put("price", "-1");
        mvc.perform(jsonRequest(post(s.base() + "/services"), s.owner().token(), bad)).andExpect(status().isBadRequest());
        Map<String, Object> foreign = service(999_999, "Foreign branch");
        mvc.perform(jsonRequest(post(s.base() + "/services"), s.owner().token(), foreign))
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("INVALID_BRANCH"));

        Person other = verifiedCustomer();
        createDraft(other, subcategoryId("gym"), "Other Gym");
        mvc.perform(authed(get(s.base() + "/services"), other.token())).andExpect(status().isNotFound());
        mvc.perform(jsonRequest(put(s.base() + "/services/" + s.serviceId()), other.token(), service(s.branchId(), "x")))
                .andExpect(status().isNotFound());
        mvc.perform(authed(get(s.base() + "/staff"), other.token())).andExpect(status().isNotFound());
    }

    @Test
    void invitationFlowRequiresTheInvitedEmailAndGrantsStaffRole() throws Exception {
        Setup s = setup();
        String staffEmail = uniqueEmail();
        channels.clear();
        JsonNode invited = read(mvc.perform(jsonRequest(post(s.base() + "/staff/invitations"), s.owner().token(),
                        Map.of("email", staffEmail.toUpperCase(), "profile",
                                profile("Dr. Meena", s.branchId(), s.serviceId(), "CAN_EDIT_HOURS"))))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(invited.path("status").asText()).isEqualTo("INVITED");
        assertThat(invited.path("invitation").path("open").asBoolean()).isTrue();
        long staffId = invited.path("id").asLong();
        mvc.perform(jsonRequest(post(s.base() + "/staff/invitations"), s.owner().token(),
                        Map.of("email", staffEmail, "profile", profile("Again", s.branchId(), s.serviceId()))))
                .andExpect(jsonPath("$.error.code").value("STAFF_ALREADY_EXISTS"));

        String token = channels.lastTokenFor(staffEmail);
        mvc.perform(post("/api/v1/public/staff-invitations/preview").contentType("application/json")
                        .content(body(Map.of("token", token))))
                .andExpect(jsonPath("$.data.organizationName").value("Team Dental"))
                .andExpect(jsonPath("$.data.email").value(staffEmail));

        // A different account can't use the link.
        Person stranger = verifiedCustomer();
        mvc.perform(jsonRequest(post("/api/v1/staff-invitations/accept"), stranger.token(), Map.of("token", token)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("INVITATION_EMAIL_MISMATCH"));

        Session invitee = register(staffEmail);
        mvc.perform(jsonRequest(post("/api/v1/staff-invitations/accept"), invitee.accessToken(), Map.of("token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.organizationName").value("Team Dental"))
                .andExpect(jsonPath("$.data.services[0].name").value("Check-up"));
        mvc.perform(me(invitee.accessToken()))
                .andExpect(jsonPath("$.data.roles[?(@ == 'STAFF')]").exists())
                .andExpect(jsonPath("$.data.emailVerified").value(true));
        mvc.perform(jsonRequest(post("/api/v1/staff-invitations/accept"), invitee.accessToken(), Map.of("token", token)))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        // Staff with CAN_EDIT_HOURS set their own availability, inside their assigned branch only.
        String own = "/api/v1/staff/profiles/" + staffId + "/availability";
        mvc.perform(jsonRequest(put(own), invitee.accessToken(), Map.of("windows", List.of(
                        Map.of("branchId", s.branchId(), "dayOfWeek", 1, "startTime", "09:00", "endTime", "12:00")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.availability.length()").value(1));
        mvc.perform(authed(get("/api/v1/staff/workplaces"), invitee.accessToken()))
                .andExpect(jsonPath("$.data[0].profile.availability[0].startTime").value("09:00:00"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'STAFF_JOINED' AND entity_id = ?",
                Integer.class, String.valueOf(s.orgId()))).isEqualTo(1);
    }

    @Test
    void staffWithoutEditPermissionCannotChangeTheirHours() throws Exception {
        Setup s = setup();
        long staffId = read(mvc.perform(jsonRequest(post(s.base() + "/staff/self"), s.owner().token(),
                profile("Owner Doc", s.branchId(), s.serviceId()))).andExpect(status().isOk()).andReturn())
                .path("data").path("id").asLong();
        mvc.perform(me(s.owner().token())).andExpect(jsonPath("$.data.roles[?(@ == 'STAFF')]").exists());
        mvc.perform(jsonRequest(put("/api/v1/staff/profiles/" + staffId + "/availability"), s.owner().token(),
                Map.of("windows", List.of()))).andExpect(status().isForbidden());
        mvc.perform(jsonRequest(post(s.base() + "/staff/self"), s.owner().token(),
                profile("Twice", s.branchId(), s.serviceId()))).andExpect(status().isConflict());
    }

    @Test
    void slotsFollowAvailabilityLeaveAndApproval() throws Exception {
        Setup s = setup();
        long staffId = read(mvc.perform(jsonRequest(post(s.base() + "/staff/self"), s.owner().token(),
                profile("Owner Doc", s.branchId(), s.serviceId()))).andReturn()).path("data").path("id").asLong();
        String avail = s.base() + "/staff/" + staffId + "/availability";
        mvc.perform(jsonRequest(put(avail), s.owner().token(), Map.of("windows", List.of(
                        Map.of("branchId", s.branchId(), "dayOfWeek", 1, "startTime", "09:00", "endTime", "11:00"),
                        Map.of("branchId", s.branchId(), "dayOfWeek", 1, "startTime", "10:30", "endTime", "12:00")))))
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("OVERLAPPING_WINDOWS"));
        mvc.perform(jsonRequest(put(avail), s.owner().token(), Map.of("windows", List.of(
                        Map.of("branchId", s.branchId(), "dayOfWeek", 1, "startTime", "09:00", "endTime", "11:00"),
                        Map.of("branchId", s.branchId(), "dayOfWeek", 1, "startTime", "12:30", "endTime", "15:00")))))
                .andExpect(status().isOk());

        LocalDate monday = nextMonday();
        String preview = s.base() + "/slots?branchId=" + s.branchId() + "&serviceId=" + s.serviceId() + "&date=" + monday;
        JsonNode day = read(mvc.perform(authed(get(preview), s.owner().token())).andExpect(status().isOk()).andReturn())
                .path("data");
        assertThat(day.path("outcome").asText()).isEqualTo("OPEN");
        assertThat(day.path("price").decimalValue()).isEqualByComparingTo("450.50");
        assertThat(day.path("slots")).extracting(n -> n.path("time").asText())
                .containsExactly("09:00", "09:30", "10:00", "10:30", "12:30", "14:00", "14:30");
        assertThat(day.path("staff").get(0).path("displayName").asText()).isEqualTo("Owner Doc");

        // Leave 10:00–14:00 IST removes the overlapping slots.
        mvc.perform(jsonRequest(post(s.base() + "/staff/" + staffId + "/leaves"), s.owner().token(), Map.of(
                "startsAt", ZonedDateTime.of(monday, LocalTime.of(10, 0), IST).toInstant().toString(),
                "endsAt", ZonedDateTime.of(monday, LocalTime.of(14, 0), IST).toInstant().toString(),
                "reason", "Conference"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upcomingLeave[0].reason").value("Conference"));
        day = read(mvc.perform(authed(get(preview), s.owner().token())).andReturn()).path("data");
        assertThat(day.path("slots")).extracting(n -> n.path("time").asText())
                .containsExactly("09:00", "09:30", "14:00", "14:30");

        // Sunday: branch closed.
        LocalDate sunday = monday.minusDays(1);
        mvc.perform(authed(get(s.base() + "/slots?branchId=" + s.branchId() + "&serviceId=" + s.serviceId()
                + "&date=" + sunday), s.owner().token())).andExpect(jsonPath("$.data.outcome").value("CLOSED"));

        // Public slots only once the business is approved.
        String pub = "/api/v1/public/branches/" + s.branchId() + "/services/" + s.serviceId() + "/slots?date=" + monday;
        mvc.perform(get(pub)).andExpect(status().isNotFound());
        uploadMandatoryDocuments(s.owner(), s.orgId());
        mvc.perform(authed(post(s.base() + "/submit"), s.owner().token())).andExpect(status().isOk());
        mvc.perform(authed(post("/api/v1/admin/organizations/" + s.orgId() + "/approve"), admin().token()))
                .andExpect(status().isOk());
        mvc.perform(get(pub)).andExpect(status().isOk()).andExpect(jsonPath("$.data.slots.length()").value(4));
    }

    @Test
    void revokingAnInvitationRemovesThePlaceholder() throws Exception {
        Setup s = setup();
        long staffId = read(mvc.perform(jsonRequest(post(s.base() + "/staff/invitations"), s.owner().token(),
                Map.of("email", uniqueEmail(), "profile", profile("Temp", s.branchId(), s.serviceId())))).andReturn())
                .path("data").path("id").asLong();
        mvc.perform(authed(delete(s.base() + "/staff/" + staffId + "/invitation"), s.owner().token()))
                .andExpect(status().isOk());
        mvc.perform(authed(get(s.base() + "/staff"), s.owner().token())).andExpect(jsonPath("$.data.length()").value(0));
    }
}
