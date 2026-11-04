package com.bookeasy.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bookeasy.support.OrganizationTestBase;
import com.fasterxml.jackson.databind.JsonNode;

class DiscoveryIT extends OrganizationTestBase {

    /** Approved business with one branch at the given point and one service. Returns its slug. */
    private String approved(String name, double lat, double lng, String serviceName, String price) throws Exception {
        Person owner = verifiedCustomer();
        long orgId = createDraft(owner, subcategoryId("dental-clinic"), name).path("summary").path("id").asLong();
        Map<String, Object> branch = branchBody("Main");
        branch.put("latitude", lat);
        branch.put("longitude", lng);
        long branchId = read(mvc.perform(jsonRequest(post("/api/v1/owner/organizations/" + orgId + "/branches"),
                owner.token(), branch)).andExpect(status().isOk()).andReturn()).path("data").path("id").asLong();
        mvc.perform(jsonRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/v1/owner/organizations/" + orgId + "/branches/" + branchId + "/hours"), owner.token(), weekdayHours()));
        mvc.perform(jsonRequest(post("/api/v1/owner/organizations/" + orgId + "/services"), owner.token(), Map.of(
                "name", serviceName, "durationMinutes", 30, "price", price,
                "branches", List.of(Map.of("branchId", branchId))))).andExpect(status().isOk());
        uploadMandatoryDocuments(owner, orgId);
        mvc.perform(authed(post("/api/v1/owner/organizations/" + orgId + "/submit"), owner.token())).andExpect(status().isOk());
        mvc.perform(authed(post("/api/v1/admin/organizations/" + orgId + "/approve"), admin().token())).andExpect(status().isOk());
        return read(mvc.perform(authed(get("/api/v1/owner/organizations/" + orgId), owner.token())).andReturn())
                .path("data").path("summary").path("slug").asText();
    }

    private JsonNode search(String query) throws Exception {
        return read(mvc.perform(get("/api/v1/public/search?" + query)).andExpect(status().isOk()).andReturn()).path("data");
    }

    @Test
    void findsNearbyApprovedBranchesWithDistanceFiltersAndSorting() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        String near = approved("Near Dental " + tag, 13.0418, 80.2341, "Root canal " + tag, "2500.00");
        approved("Close Dental " + tag, 13.0600, 80.2500, "Cleaning " + tag, "800.00");
        approved("Far Dental " + tag, 12.9716, 77.5946, "Braces " + tag, "40000.00"); // Bengaluru
        createDraft(verifiedCustomer(), subcategoryId("dental-clinic"), "Draft Dental " + tag); // never listed

        JsonNode byDistance = search("lat=13.04&lng=80.23&radiusKm=10&q=" + tag);
        assertThat(byDistance.path("items")).extracting(h -> h.path("name").asText())
                .containsExactly("Near Dental " + tag, "Close Dental " + tag);
        JsonNode first = byDistance.path("items").get(0);
        assertThat(first.path("slug").asText()).isEqualTo(near);
        assertThat(first.path("distanceKm").asDouble()).isLessThan(1.0);
        assertThat(first.path("fromPrice").decimalValue()).isEqualByComparingTo("2500.00");
        assertThat(first.path("subcategoryName").path("en").asText()).isEqualTo("Dental clinic");

        assertThat(search("lat=13.04&lng=80.23&radiusKm=10&sort=PRICE&q=" + tag).path("items"))
                .extracting(h -> h.path("name").asText()).first().isEqualTo("Close Dental " + tag);
        assertThat(search("lat=13.04&lng=80.23&radiusKm=10&q=cleaning " + tag).path("items"))
                .extracting(h -> h.path("name").asText()).containsExactly("Close Dental " + tag);
        assertThat(search("lat=12.97&lng=77.59&radiusKm=5&q=" + tag).path("items"))
                .extracting(h -> h.path("name").asText()).containsExactly("Far Dental " + tag);
        long gym = jdbc.queryForObject("SELECT category_id FROM subcategories WHERE slug = 'gym'", Long.class);
        assertThat(search("lat=13.04&lng=80.23&radiusKm=10&categoryId=" + gym + "&q=" + tag).path("items")).isEmpty();

        mvc.perform(get("/api/v1/public/search?lat=51.5&lng=-0.12")).andExpect(status().isBadRequest());
    }

    @Test
    void publicProfileAndSuggestionsShowOnlyApprovedBusinesses() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        String slug = approved("Smile Studio " + tag, 13.05, 80.24, "Whitening", "3000.00");
        mvc.perform(get("/api/v1/public/organizations/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Smile Studio " + tag))
                .andExpect(jsonPath("$.data.bookingWorkflow").value("HEALTHCARE"))
                .andExpect(jsonPath("$.data.branches[0].hours.length()").value(6))
                .andExpect(jsonPath("$.data.services[0].name").value("Whitening"));

        long draftId = createDraft(verifiedCustomer(), subcategoryId("gym"), "Hidden Gym " + tag).path("summary").path("id").asLong();
        String draftSlug = jdbc.queryForObject("SELECT slug FROM organizations WHERE id = ?", String.class, draftId);
        mvc.perform(get("/api/v1/public/organizations/" + draftSlug)).andExpect(status().isNotFound());

        JsonNode s = read(mvc.perform(get("/api/v1/public/suggest?q=" + tag)).andReturn()).path("data");
        assertThat(s).extracting(n -> n.path("label").asText()).contains("Smile Studio " + tag).doesNotContain("Hidden Gym " + tag);
        JsonNode sub = read(mvc.perform(get("/api/v1/public/suggest?q=dental")).andReturn()).path("data");
        assertThat(sub).anySatisfy(n -> assertThat(n.path("type").asText()).isEqualTo("SUBCATEGORY"));
    }
}
