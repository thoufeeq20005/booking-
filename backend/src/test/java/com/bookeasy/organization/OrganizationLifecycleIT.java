package com.bookeasy.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bookeasy.support.OrganizationTestBase;
import com.fasterxml.jackson.databind.JsonNode;

class OrganizationLifecycleIT extends OrganizationTestBase {

    @Test
    void customerRegistersBusinessAndBecomesOwnerImmediately() throws Exception {
        Person p = verifiedCustomer();
        mvc.perform(authed(get("/api/v1/owner/organizations"), p.token())).andExpect(status().isForbidden());

        JsonNode detail = createDraft(p, subcategoryId("dental-clinic"), "Sunrise Dental Care");
        assertThat(detail.path("summary").path("status").asText()).isEqualTo("DRAFT");
        assertThat(detail.path("summary").path("slug").asText()).isEqualTo("sunrise-dental-care");
        assertThat(detail.path("summary").path("categoryName").path("en").asText()).isEqualTo("Healthcare");
        assertThat(detail.path("contactPhone").asText()).isEqualTo("+919876543210");
        assertThat(detail.path("contactEmail").asText()).isEqualTo("front.desk@example.test");
        assertThat(detail.path("website").asText()).isEqualTo("https://sunrise-clinic.example.in");
        assertThat(detail.path("missingSteps")).extracting(JsonNode::asText)
                .containsExactly("BRANCH", "DOCUMENTS");
        assertThat(detail.path("documents")).hasSize(3);

        // Same access token: the role is read from the database on each request.
        mvc.perform(authed(get("/api/v1/owner/organizations"), p.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Sunrise Dental Care"));
        mvc.perform(me(p.token())).andExpect(jsonPath("$.data.roles[?(@ == 'BUSINESS_OWNER')]").exists());

        long orgId = detail.path("summary").path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'ORGANIZATION_CREATED' "
                + "AND entity_id = ?", Integer.class, String.valueOf(orgId))).isEqualTo(1);
    }

    @Test
    void submissionListsEveryMissingStepThenSucceeds() throws Exception {
        Person p = verifiedCustomer();
        jdbc.update("UPDATE users SET email_verified_at = NULL WHERE id = ?", p.id()); // unverified owners may submit
        long orgId = createDraft(p, subcategoryId("general-clinic"), "Asha Clinic").path("summary").path("id").asLong();

        mvc.perform(authed(post("/api/v1/owner/organizations/" + orgId + "/submit"), p.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ORGANIZATION_INCOMPLETE"))
                .andExpect(jsonPath("$.error.fieldErrors[*].code").value(
                        org.hamcrest.Matchers.containsInAnyOrder("BRANCH", "DOCUMENTS")));

        long branchId = read(mvc.perform(jsonRequest(post("/api/v1/owner/organizations/" + orgId + "/branches"),
                p.token(), branchBody("Main"))).andExpect(status().isOk()).andReturn()).path("data").path("id").asLong();
        assertThat(ownerDetail(p, orgId).path("missingSteps")).extracting(JsonNode::asText)
                .contains("WORKING_HOURS").doesNotContain("BRANCH");
        mvc.perform(jsonRequest(put("/api/v1/owner/organizations/" + orgId + "/branches/" + branchId + "/hours"),
                p.token(), weekdayHours())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hours.length()").value(6));
        uploadMandatoryDocuments(p, orgId);

        channels.clear();
        mvc.perform(authed(post("/api/v1/owner/organizations/" + orgId + "/submit"), p.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.status").value("PENDING"))
                .andExpect(jsonPath("$.data.editable.identity").value(false))
                .andExpect(jsonPath("$.data.editable.operations").value(false))
                .andExpect(jsonPath("$.data.history[0].action").value("SUBMITTED"));
        assertThat(channels.emailsTo(p.email())).hasSize(1);
        assertThat(channels.emailsTo(p.email()).get(0).subject()).contains("Asha Clinic");

        // Under review nothing can change, and it can't be submitted twice.
        mvc.perform(jsonRequest(put("/api/v1/owner/organizations/" + orgId), p.token(),
                orgBody(subcategoryId("general-clinic"), "Renamed"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORGANIZATION_NOT_EDITABLE"));
        mvc.perform(authed(post("/api/v1/owner/organizations/" + orgId + "/submit"), p.token()))
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void adminApprovesAcceptingDocumentsAuditsAndEmailsOwner() throws Exception {
        Person owner = verifiedCustomer();
        long orgId = pendingOrganization(owner, "Green Leaf Clinic");
        Person admin = admin();
        channels.clear();

        mvc.perform(authed(get("/api/v1/admin/organizations").param("status", "PENDING").param("size", "100"),
                        admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + orgId + ")].ownerEmail").value(owner.email()));
        mvc.perform(authed(get("/api/v1/admin/organizations/" + orgId), admin.token()))
                .andExpect(jsonPath("$.data.owners[0].email").value(owner.email()))
                .andExpect(jsonPath("$.data.owners[0].emailVerified").value(true));

        mvc.perform(authed(post("/api/v1/admin/organizations/" + orgId + "/approve"), admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.summary.approvedAt").exists())
                .andExpect(jsonPath("$.data.documents[?(@.document.status == 'SUBMITTED')]").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'ORGANIZATION_APPROVED' "
                + "AND entity_id = ? AND actor_user_id = ?", Integer.class, String.valueOf(orgId), admin.id()))
                .isEqualTo(1);
        assertThat(channels.emailsTo(owner.email())).singleElement()
                .satisfies(m -> assertThat(m.textBody()).contains("Green Leaf Clinic", "/owner/organizations/" + orgId));

        // After approval: operations stay editable, identity is locked, documents can't change.
        JsonNode detail = ownerDetail(owner, orgId);
        assertThat(detail.path("editable").path("identity").asBoolean()).isFalse();
        assertThat(detail.path("editable").path("operations").asBoolean()).isTrue();
        Map<String, Object> body = orgBody(subcategoryId("general-clinic"), "Green Leaf Clinic");
        body.put("description", "Now open on Saturdays");
        mvc.perform(jsonRequest(put("/api/v1/owner/organizations/" + orgId), owner.token(), body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.description").value("Now open on Saturdays"));
        body.put("name", "Green Leaf Hospital");
        mvc.perform(jsonRequest(put("/api/v1/owner/organizations/" + orgId), owner.token(), body))
                .andExpect(jsonPath("$.error.code").value("ORGANIZATION_NOT_EDITABLE"));
        long reqId = detail.path("documents").get(0).path("requirement").path("id").asLong();
        upload(owner, orgId, reqId, "new.pdf", "application/pdf", PDF)
                .andExpect(jsonPath("$.error.code").value("ORGANIZATION_NOT_EDITABLE"));

        // Approving twice is not a valid transition.
        mvc.perform(authed(post("/api/v1/admin/organizations/" + orgId + "/approve"), admin.token()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void rejectedDocumentBlocksApprovalUntilOwnerFixesAndResubmits() throws Exception {
        Person owner = verifiedCustomer();
        long orgId = pendingOrganization(owner, "Lotus Clinic");
        Person admin = admin();
        JsonNode adminDetail = read(mvc.perform(authed(get("/api/v1/admin/organizations/" + orgId), admin.token()))
                .andReturn()).path("data");
        JsonNode slot = adminDetail.path("documents").get(0);
        long docId = slot.path("document").path("id").asLong();
        long reqId = slot.path("requirement").path("id").asLong();

        // Admin can open the private file.
        mvc.perform(authed(get("/api/v1/admin/organizations/" + orgId + "/documents/" + docId + "/file"), admin.token()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Type", "application/pdf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        String reviewUrl = "/api/v1/admin/organizations/" + orgId + "/documents/" + docId;
        mvc.perform(jsonRequest(patch(reviewUrl), admin.token(), Map.of("status", "REJECTED")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fieldErrors[0].field").value("note"));
        mvc.perform(jsonRequest(patch(reviewUrl), admin.token(), Map.of("status", "REJECTED", "note", "Blurred scan")))
                .andExpect(status().isOk());
        mvc.perform(authed(post("/api/v1/admin/organizations/" + orgId + "/approve"), admin.token()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DOCUMENTS_NOT_ACCEPTED"));

        mvc.perform(jsonRequest(post("/api/v1/admin/organizations/" + orgId + "/reject"), admin.token(),
                Map.of("reason", ""))).andExpect(status().isBadRequest());
        channels.clear();
        mvc.perform(jsonRequest(post("/api/v1/admin/organizations/" + orgId + "/reject"), admin.token(),
                        Map.of("reason", "Please upload a clear copy of your ID")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.status").value("REJECTED"));
        assertThat(channels.emailsTo(owner.email()).get(0).textBody()).contains("Please upload a clear copy of your ID");

        JsonNode mine = ownerDetail(owner, orgId);
        assertThat(mine.path("summary").path("statusReason").asText()).isEqualTo("Please upload a clear copy of your ID");
        assertThat(mine.path("missingSteps")).extracting(JsonNode::asText).containsExactly("DOCUMENTS");
        assertThat(mine.path("documents").get(0).path("document").path("reviewerNote").asText()).isEqualTo("Blurred scan");

        upload(owner, orgId, reqId, "id-clear.png", "image/png", PNG).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.document.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.document.fileName").value("id-clear.png"));
        mvc.perform(authed(post("/api/v1/owner/organizations/" + orgId + "/submit"), owner.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.status").value("PENDING"));
        mvc.perform(authed(post("/api/v1/admin/organizations/" + orgId + "/approve"), admin.token()))
                .andExpect(status().isOk());

        JsonNode history = ownerDetail(owner, orgId).path("history");
        assertThat(history).extracting(h -> h.path("action").asText())
                .containsExactly("SUBMITTED", "REJECTED", "SUBMITTED", "APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM uploaded_files WHERE owner_user_id = ?", Integer.class,
                owner.id())).as("the replaced file's record is removed").isEqualTo(3);
    }

    @Test
    void suspendAndReinstateAreAuditedAndRequireAReason() throws Exception {
        Person owner = verifiedCustomer();
        long orgId = pendingOrganization(owner, "Kavya Clinic");
        Person admin = admin();
        // Only approved businesses can be suspended.
        mvc.perform(jsonRequest(post("/api/v1/admin/organizations/" + orgId + "/suspend"), admin.token(),
                Map.of("reason", "Repeated customer complaints"))).andExpect(status().isConflict());
        mvc.perform(authed(post("/api/v1/admin/organizations/" + orgId + "/approve"), admin.token()))
                .andExpect(status().isOk());
        mvc.perform(jsonRequest(post("/api/v1/admin/organizations/" + orgId + "/suspend"), admin.token(), Map.of()))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post("/api/v1/admin/organizations/" + orgId + "/suspend"), admin.token(),
                Map.of("reason", "Repeated customer complaints"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.status").value("SUSPENDED"));

        // A suspended business is read-only for its owner.
        mvc.perform(jsonRequest(post("/api/v1/owner/organizations/" + orgId + "/branches"), owner.token(),
                branchBody("Second"))).andExpect(jsonPath("$.error.code").value("ORGANIZATION_NOT_EDITABLE"));

        mvc.perform(authed(post("/api/v1/admin/organizations/" + orgId + "/reinstate"), admin.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.status").value("APPROVED"));
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE entity_type = 'ORGANIZATION' AND entity_id = ? "
                + "ORDER BY id", String.class, String.valueOf(orgId)))
                .containsSubsequence("ORGANIZATION_APPROVED", "ORGANIZATION_SUSPENDED", "ORGANIZATION_REINSTATED");
    }

    @Test
    void adminCannotReviewOwnBusiness() throws Exception {
        Person admin = admin();
        jdbc.update("UPDATE users SET email_verified_at = UTC_TIMESTAMP(6) WHERE id = ?", admin.id());
        long orgId = pendingOrganization(admin, "Admin's Own Salon");
        mvc.perform(authed(post("/api/v1/admin/organizations/" + orgId + "/approve"), admin.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void onePersonMayHaveAtMostThreeUnfinishedApplications() throws Exception {
        Person p = verifiedCustomer();
        long sub = subcategoryId("gym");
        for (int i = 0; i < 3; i++) {
            createDraft(p, sub, "Gym " + i);
        }
        mvc.perform(jsonRequest(post("/api/v1/business/organizations"), p.token(), orgBody(sub, "Gym 4")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORGANIZATION_LIMIT_REACHED"));
    }

    @Test
    void invalidContactDetailsAreRejectedPerField() throws Exception {
        Person p = verifiedCustomer();
        Map<String, Object> body = orgBody(subcategoryId("gym"), "Iron Gym");
        body.put("contactPhone", "12345");
        mvc.perform(jsonRequest(post("/api/v1/business/organizations"), p.token(), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("contactPhone"));
        body.put("contactPhone", null);
        body.put("website", "javascript:alert(1)");
        mvc.perform(jsonRequest(post("/api/v1/business/organizations"), p.token(), body))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("website"));
        body.put("website", null);
        body.put("gstin", "not-a-gstin");
        mvc.perform(jsonRequest(post("/api/v1/business/organizations"), p.token(), body))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("gstin"));
        body.put("gstin", "33aabcu9603r1zx");
        mvc.perform(jsonRequest(post("/api/v1/business/organizations"), p.token(), body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.gstin").value("33AABCU9603R1ZX"));
    }

    @Test
    void changingCategoryDropsDocumentsOfTheOldCategory() throws Exception {
        Person p = verifiedCustomer();
        long orgId = createDraft(p, subcategoryId("general-clinic"), "Switcher").path("summary").path("id").asLong();
        uploadMandatoryDocuments(p, orgId);
        mvc.perform(jsonRequest(put("/api/v1/owner/organizations/" + orgId), p.token(),
                orgBody(subcategoryId("unisex-salon"), "Switcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.categoryName.en").value("Salon & spa"))
                .andExpect(jsonPath("$.data.documents[?(@.document)]").isEmpty());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization_documents WHERE organization_id = ?",
                Integer.class, orgId)).isZero();
    }
}
