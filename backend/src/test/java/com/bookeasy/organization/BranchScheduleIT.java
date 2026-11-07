package com.bookeasy.organization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bookeasy.support.OrganizationTestBase;

class BranchScheduleIT extends OrganizationTestBase {

    private Person owner;
    private String branchUrl;

    @BeforeEach
    void setUp() throws Exception {
        owner = verifiedCustomer();
        long orgId = createDraft(owner, subcategoryId("yoga-studio"), "Calm Yoga").path("summary").path("id").asLong();
        branchUrl = "/api/v1/owner/organizations/" + orgId + "/branches/" + addBranch(owner, orgId);
    }

    private org.springframework.test.web.servlet.ResultActions setHours(Object... days) throws Exception {
        return mvc.perform(jsonRequest(put(branchUrl + "/hours"), owner.token(), Map.of("days", List.of(days))));
    }

    @Test
    void weeklyHoursAreValidatedAndReplacedAsAWhole() throws Exception {
        setHours(Map.of("dayOfWeek", 1, "opensAt", "18:00", "closesAt", "09:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("CLOSES_BEFORE_OPENS"));
        setHours(Map.of("dayOfWeek", 1, "opensAt", "09:00", "closesAt", "18:00", "breakStart", "08:00",
                "breakEnd", "09:30"))
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("BREAK_OUTSIDE_HOURS"));
        setHours(Map.of("dayOfWeek", 1, "opensAt", "09:00", "closesAt", "18:00", "breakStart", "13:00"))
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("BREAK_INCOMPLETE"));
        setHours(Map.of("dayOfWeek", 2, "opensAt", "09:00", "closesAt", "12:00"),
                Map.of("dayOfWeek", 2, "opensAt", "14:00", "closesAt", "18:00"))
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("DUPLICATE_DAY"));
        setHours(Map.of("dayOfWeek", 8, "opensAt", "09:00", "closesAt", "18:00"))
                .andExpect(status().isBadRequest());

        setHours(Map.of("dayOfWeek", 7, "opensAt", "06:00", "closesAt", "08:30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hours.length()").value(1))
                .andExpect(jsonPath("$.data.hours[0].dayOfWeek").value(7))
                .andExpect(jsonPath("$.data.hours[0].opensAt").value("06:00:00"));

        // Closing every day is allowed for a branch, but submission then asks for hours.
        setHours().andExpect(status().isOk()).andExpect(jsonPath("$.data.hours.length()").value(0));
    }

    @Test
    void holidaysAreFutureOnlyUniqueAndDeletable() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        mvc.perform(jsonRequest(post(branchUrl + "/holidays"), owner.token(),
                        Map.of("date", today.minusDays(1).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("DATE_IN_PAST"));
        mvc.perform(jsonRequest(post(branchUrl + "/holidays"), owner.token(),
                        Map.of("date", today.plusYears(3).toString())))
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("DATE_TOO_FAR"));

        String diwali = today.plusDays(20).toString();
        long id = read(mvc.perform(jsonRequest(post(branchUrl + "/holidays"), owner.token(),
                        Map.of("date", diwali, "reason", "Diwali")))
                .andExpect(status().isOk()).andReturn()).path("data").path("id").asLong();
        mvc.perform(jsonRequest(post(branchUrl + "/holidays"), owner.token(), Map.of("date", diwali)))
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("DUPLICATE_DATE"));

        mvc.perform(authed(get(branchUrl + "/holidays"), owner.token()))
                .andExpect(jsonPath("$.data[0].date").value(diwali))
                .andExpect(jsonPath("$.data[0].reason").value("Diwali"));

        // Past holidays drop out of the upcoming list.
        long branchId = Long.parseLong(branchUrl.substring(branchUrl.lastIndexOf('/') + 1));
        jdbc.update("INSERT INTO holidays (branch_id, holiday_date, reason, created_at) VALUES (?, ?, 'old', UTC_TIMESTAMP(6))",
                branchId, java.sql.Date.valueOf(today.minusDays(10)));
        mvc.perform(authed(get(branchUrl + "/holidays"), owner.token())).andExpect(jsonPath("$.data.length()").value(1));

        mvc.perform(authed(delete(branchUrl + "/holidays/" + id), owner.token())).andExpect(status().isOk());
        mvc.perform(authed(get(branchUrl + "/holidays"), owner.token())).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void branchesCanBeDeletedBeforeApprovalOnly() throws Exception {
        mvc.perform(authed(delete(branchUrl), owner.token())).andExpect(status().isOk());
        mvc.perform(authed(get(branchUrl + "/holidays"), owner.token())).andExpect(status().isNotFound());
    }
}
