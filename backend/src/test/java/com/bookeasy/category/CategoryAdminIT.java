package com.bookeasy.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bookeasy.support.OrganizationTestBase;
import com.fasterxml.jackson.databind.JsonNode;

class CategoryAdminIT extends OrganizationTestBase {

    private static Map<String, Object> category(String slug, boolean active) {
        Map<String, Object> m = new HashMap<>();
        m.put("slug", slug);
        m.put("name", Map.of("en", "Pet care", "ta", "செல்லப்பிராணி பராமரிப்பு"));
        m.put("icon", "paw-print");
        m.put("bookingWorkflow", "GENERIC");
        m.put("active", active);
        m.put("sortOrder", 90);
        return m;
    }

    private static JsonNode find(JsonNode tree, String slug) {
        for (JsonNode c : tree) {
            if (c.path("slug").asText().equals(slug)) {
                return c;
            }
        }
        return null;
    }

    @Test
    void publicCatalogueHasSeededCategoriesInAllLanguages() throws Exception {
        JsonNode tree = read(mvc.perform(get("/api/v1/public/categories")).andExpect(status().isOk()).andReturn())
                .path("data");
        JsonNode health = find(tree, "healthcare");
        assertThat(health).isNotNull();
        assertThat(health.path("name").size()).isEqualTo(6);
        assertThat(health.path("bookingWorkflow").asText()).isEqualTo("HEALTHCARE");
        assertThat(health.path("subcategories")).extracting(s -> s.path("slug").asText())
                .contains("general-clinic", "dental-clinic");
        assertThat(health.has("documentRequirements")).as("requirements are admin-only").isFalse();
    }

    @Test
    void adminManagesCategoriesSubcategoriesAndRequirementsWithAudit() throws Exception {
        Person admin = admin();
        String slug = "pets-" + UUID.randomUUID().toString().substring(0, 8);

        JsonNode tree = read(mvc.perform(jsonRequest(post("/api/v1/admin/categories"), admin.token(),
                category(slug, true))).andExpect(status().isOk()).andReturn()).path("data");
        long id = find(tree, slug).path("id").asLong();
        mvc.perform(jsonRequest(post("/api/v1/admin/categories"), admin.token(), category(slug, true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SLUG_TAKEN"));

        // Without an active subcategory a category isn't offered publicly.
        assertThat(find(read(mvc.perform(get("/api/v1/public/categories")).andReturn()).path("data"), slug)).isNull();

        String base = "/api/v1/admin/categories/" + id;
        tree = read(mvc.perform(jsonRequest(post(base + "/subcategories"), admin.token(),
                Map.of("slug", "grooming", "name", Map.of("en", "Grooming")))).andExpect(status().isOk()).andReturn())
                .path("data");
        long subId = find(tree, slug).path("subcategories").get(0).path("id").asLong();
        assertThat(find(read(mvc.perform(get("/api/v1/public/categories")).andReturn()).path("data"), slug)).isNotNull();

        Map<String, Object> req = new HashMap<>(Map.of("code", "VET_LICENCE", "label", Map.of("en", "Vet licence"),
                "allowedTypes", List.of("application/pdf"), "maxSizeBytes", 2_000_000));
        mvc.perform(jsonRequest(post(base + "/document-requirements"), admin.token(), req)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.slug == '" + slug + "')].documentRequirements[0].code").value("VET_LICENCE"));
        req.put("allowedTypes", List.of("application/x-msdownload"));
        req.put("code", "OTHER");
        mvc.perform(jsonRequest(post(base + "/document-requirements"), admin.token(), req))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("UNSUPPORTED_TYPE"));

        // Missing English name, unsupported language.
        Map<String, Object> bad = category(slug, true);
        bad.put("name", Map.of("ta", "தமிழ் மட்டும்"));
        mvc.perform(jsonRequest(put(base), admin.token(), bad)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("name"));
        bad.put("name", Map.of("en", "Pets", "fr", "Animaux"));
        mvc.perform(jsonRequest(put(base), admin.token(), bad))
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("UNSUPPORTED_LANGUAGE"));

        // A business uses the subcategory: delete is refused, deactivation hides it from new registrations.
        Person owner = verifiedCustomer();
        createDraft(owner, subId, "Happy Paws");
        mvc.perform(authed(delete(base + "/subcategories/" + subId), admin.token()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CATEGORY_IN_USE"));
        mvc.perform(authed(delete(base), admin.token())).andExpect(jsonPath("$.error.code").value("CATEGORY_IN_USE"));
        mvc.perform(jsonRequest(put(base), admin.token(), category(slug, false))).andExpect(status().isOk());
        assertThat(find(read(mvc.perform(get("/api/v1/public/categories")).andReturn()).path("data"), slug)).isNull();
        mvc.perform(jsonRequest(post("/api/v1/business/organizations"), verifiedCustomer().token(),
                        orgBody(subId, "Too Late Pets")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("INACTIVE"));

        assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE actor_user_id = ? ORDER BY id", String.class,
                admin.id())).containsSubsequence("CATEGORY_CREATED", "SUBCATEGORY_CREATED", "DOC_REQUIREMENT_CREATED",
                "CATEGORY_UPDATED");
    }

    @Test
    void unusedCategoryCanBeDeletedWithItsChildren() throws Exception {
        Person admin = admin();
        String slug = "tmp-" + UUID.randomUUID().toString().substring(0, 8);
        long id = find(read(mvc.perform(jsonRequest(post("/api/v1/admin/categories"), admin.token(),
                category(slug, true))).andReturn()).path("data"), slug).path("id").asLong();
        mvc.perform(jsonRequest(post("/api/v1/admin/categories/" + id + "/subcategories"), admin.token(),
                Map.of("slug", "one", "name", Map.of("en", "One")))).andExpect(status().isOk());
        mvc.perform(authed(delete("/api/v1/admin/categories/" + id), admin.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.slug == '" + slug + "')]").isEmpty());
    }
}
