package com.bookeasy.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bookeasy.support.OrganizationTestBase;
import com.fasterxml.jackson.databind.JsonNode;

class DashboardIT extends OrganizationTestBase {

    @Test
    void ownerDashboardShowsOnlyOwnBusinessesWithProgressAndHolidays() throws Exception {
        Person owner = verifiedCustomer();
        long draft = createDraft(owner, subcategoryId("general-clinic"), "Draft Clinic").path("summary").path("id").asLong();
        long branch = addBranch(owner, draft);
        long pending = pendingOrganization(owner, "Pending Clinic");
        String date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(5).toString();
        mvc.perform(jsonRequest(post("/api/v1/owner/organizations/" + draft + "/branches/" + branch + "/holidays"),
                owner.token(), Map.of("date", date, "reason", "Pongal"))).andExpect(status().isOk());

        Person other = verifiedCustomer();
        createDraft(other, subcategoryId("gym"), "Someone Else's Gym");

        JsonNode d = read(mvc.perform(authed(get("/api/v1/owner/dashboard"), owner.token()))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(d.path("byStatus").path("DRAFT").asInt()).isEqualTo(1);
        assertThat(d.path("byStatus").path("PENDING").asInt()).isEqualTo(1);
        assertThat(d.path("businesses")).extracting(b -> b.path("summary").path("name").asText())
                .containsExactlyInAnyOrder("Draft Clinic", "Pending Clinic");

        JsonNode draftCard = null;
        for (JsonNode b : d.path("businesses")) {
            if (b.path("summary").path("id").asLong() == draft) {
                draftCard = b;
            }
        }
        assertThat(draftCard).isNotNull();
        assertThat(draftCard.path("activeBranches").asInt()).isEqualTo(1);
        assertThat(draftCard.path("branchesWithoutHours").asInt()).isZero();
        assertThat(draftCard.path("documents").path("required").asInt()).isEqualTo(3);
        assertThat(draftCard.path("documents").path("uploaded").asInt()).isZero();
        assertThat(draftCard.path("missingSteps")).extracting(JsonNode::asText).containsExactly("DOCUMENTS");

        assertThat(d.path("upcomingHolidays")).singleElement().satisfies(h -> {
            assertThat(h.path("date").asText()).isEqualTo(date);
            assertThat(h.path("organizationName").asText()).isEqualTo("Draft Clinic");
            assertThat(h.path("reason").asText()).isEqualTo("Pongal");
        });
        assertThat(pending).isPositive();
    }

    @Test
    void adminDashboardCountsUsersBusinessesAndQueue() throws Exception {
        Person owner = verifiedCustomer();
        long orgId = pendingOrganization(owner, "Queue Clinic");
        Person admin = admin();

        JsonNode d = read(mvc.perform(authed(get("/api/v1/admin/dashboard"), admin.token()))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(d.path("users").path("total").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(d.path("users").path("newLast7Days").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(d.path("users").path("byRole").path("SUPER_ADMIN").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(d.path("users").path("byRole").path("BUSINESS_OWNER").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(d.path("organizations").path("PENDING").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(d.path("organizations").has("SUSPENDED")).as("every status is present").isTrue();
        assertThat(d.path("reviewQueue").path("pending").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(d.path("reviewQueue").path("documentsAwaitingReview").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(d.path("recentActivity").size()).isBetween(1, 10);
        assertThat(d.path("recentActivity").get(0).path("action").asText()).isNotBlank();
        assertThat(orgId).isPositive();

        mvc.perform(authed(get("/api/v1/admin/dashboard"), owner.token())).andExpect(status().isForbidden());
        mvc.perform(authed(get("/api/v1/owner/dashboard"), admin.token())).andExpect(status().isForbidden());
    }

    @Test
    void auditLogCanBeFilteredAndIncludesActorNamesAndDetails() throws Exception {
        Person owner = verifiedCustomer();
        long orgId = pendingOrganization(owner, "Audited Clinic");
        Person admin = admin();
        mvc.perform(authed(post("/api/v1/admin/organizations/" + orgId + "/approve"), admin.token()))
                .andExpect(status().isOk());

        mvc.perform(authed(get("/api/v1/admin/audit-logs").param("entityType", "ORGANIZATION")
                        .param("entityId", String.valueOf(orgId)), admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("ORGANIZATION_APPROVED"))
                .andExpect(jsonPath("$.data.items[0].actorName").value("Test SUPER_ADMIN"))
                .andExpect(jsonPath("$.data.items[0].details.to").value("APPROVED"))
                .andExpect(jsonPath("$.data.items[?(@.action == 'ORGANIZATION_SUBMITTED')]").exists());

        mvc.perform(authed(get("/api/v1/admin/audit-logs").param("action", "ORGANIZATION_APPROVED")
                        .param("actorUserId", String.valueOf(admin.id())), admin.token()))
                .andExpect(jsonPath("$.data.totalItems").value(1));
        mvc.perform(authed(get("/api/v1/admin/audit-logs").param("action", "ORGANIZATION_APPROVED")
                        .param("from", "2000-01-01T00:00:00Z").param("to", "2000-01-02T00:00:00Z"), admin.token()))
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mvc.perform(authed(get("/api/v1/admin/audit-logs"), owner.token())).andExpect(status().isForbidden());
    }
}
