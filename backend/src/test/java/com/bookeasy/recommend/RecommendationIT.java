package com.bookeasy.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bookeasy.support.BookingTestBase;
import com.fasterxml.jackson.databind.JsonNode;

/** Offers published by businesses, and nearby recommendations built from them (rule-based: tests have no API key). */
class RecommendationIT extends BookingTestBase {

    private static final String NEAR = "lat=13.0418&lng=80.2341&radiusKm=5";

    @Test
    void publishedOffersAreRecommendedNearbyAndShownOnTheBusinessPage() throws Exception {
        Biz b = approvedClinic("INSTANT");
        String base = "/api/v1/owner/organizations/" + b.orgId() + "/promotions";
        mvc.perform(jsonRequest(post(base), b.owner().token(), Map.of("title", "Free health check-up camp", "kind", "FREE",
                "details", "BP, sugar and BMI checks this Sunday", "validUntil", LocalDate.now(IST).plusDays(5).toString())))
                .andExpect(status().isOk());
        JsonNode list = read(mvc.perform(jsonRequest(post(base), b.owner().token(), Map.of("title", "20% off full body check-up",
                "kind", "DISCOUNT", "percentOff", 20, "validFrom", LocalDate.now(IST).plusDays(10).toString()))).andReturn()).path("data");
        assertThat(list.size()).isEqualTo(2);
        mvc.perform(jsonRequest(post(base), b.owner().token(), Map.of("title", "Bad", "kind", "LOTTERY")))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(base), verifiedCustomer().token(), Map.of("title", "x", "kind", "FREE")))
                .andExpect(status().isForbidden()); // owners only

        String slug = jdbc.queryForObject("SELECT slug FROM organizations WHERE id = ?", String.class, b.orgId());
        // The upcoming discount isn't current yet, so only the free camp shows publicly.
        mvc.perform(get("/api/v1/public/organizations/" + slug + "/promotions"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Free health check-up camp"));

        JsonNode reco = read(mvc.perform(get("/api/v1/public/recommendations?" + NEAR + "&q=health check-up"))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(reco.path("source").asText()).isEqualTo("RULES");
        List<String> titles = new ArrayList<>();
        reco.path("items").forEach(i -> titles.add(i.path("offer").path("title").asText()));
        assertThat(titles).contains("Free health check-up camp").doesNotContain("20% off full body check-up");
        JsonNode first = reco.path("items").get(0).path("offer");
        assertThat(first.path("slug").asText()).isEqualTo(slug);
        assertThat(first.path("distanceKm").asDouble()).isLessThan(1.0);

        // Far away: nothing from this clinic.
        JsonNode far = read(mvc.perform(get("/api/v1/public/recommendations?lat=9.9252&lng=78.1198&radiusKm=5")).andReturn()).path("data");
        far.path("items").forEach(i -> assertThat(i.path("offer").path("slug").asText()).isNotEqualTo(slug));

        long id = list.get(0).path("id").asLong();
        mvc.perform(authed(delete(base + "/" + id), b.owner().token())).andExpect(jsonPath("$.data.length()").value(1));
    }
}
