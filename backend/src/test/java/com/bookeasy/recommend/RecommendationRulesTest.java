package com.bookeasy.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class RecommendationRulesTest {

    private static RecommendationService.Candidate c(String id, String type, String category, String title, Integer pct, double km) {
        return new RecommendationService.Candidate(id, type, "Biz " + id, "biz-" + id, category, title, null, pct, km,
                BigDecimal.valueOf(4.2), 10, null);
    }

    private final List<RecommendationService.Candidate> candidates = List.of(
            c("p1", "PACKAGE", "Gym", "Pay for 6 months, get 6 months free", null, 2.0),
            c("p2", "FREE", "General clinic", "Free health check-up camp", null, 3.0),
            c("d3", "DISCOUNT", "Café", "Up to 20% off the bill", 20, 0.5),
            c("p4", "DISCOUNT", "Café", "10% off coffee", 10, 0.6),
            c("p5", "DISCOUNT", "Café", "15% off desserts", 15, 0.7),
            c("f6", "FREE_SERVICE", "Yoga studio", "Free trial class", null, 1.0));

    @Test
    void aiPicksAreCheckedAgainstTheRealOffers() {
        RecommendationService.Recommendations r = RecommendationService.fromPicks("AI", "Here you go", List.of(
                new RecommendationService.Pick("p2", "Free check-up today", "It's free and 3 km away."),
                new RecommendationService.Pick("x99", "Made-up deal", "Not in the database"),
                new RecommendationService.Pick("p2", "Duplicate", "Same offer again"),
                new RecommendationService.Pick(" d3 ", null, "Cheap coffee nearby")), candidates);
        assertThat(r.items()).extracting(i -> i.offer().id()).containsExactly("p2", "d3");
        assertThat(r.items().get(1).headline()).isEqualTo("Up to 20% off the bill"); // missing headline: the offer title
        assertThat(r.source()).isEqualTo("AI");
    }

    @Test
    void rulesPreferFreeThingsAndBigDealsWithVarietyAndListenToTheRequest() {
        RecommendationService.Recommendations r = RecommendationService.byRules(candidates, "");
        assertThat(r.items()).hasSizeLessThanOrEqualTo(6);
        assertThat(r.items().stream().filter(i -> i.offer().category().equals("Café")).count()).isLessThanOrEqualTo(2);
        assertThat(r.items().get(0).offer().type()).isIn("FREE", "PACKAGE", "FREE_SERVICE");

        RecommendationService.Recommendations gym = RecommendationService.byRules(candidates, "gym membership");
        assertThat(gym.items().get(0).offer().id()).isEqualTo("p1");
    }
}
