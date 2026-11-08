package com.bookeasy.recommend;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.security.CurrentUser;
import com.bookeasy.common.web.ClientIpResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Nearby recommendations (public) and business offers (public read, owner write). */
public final class RecommendationControllers {

    private RecommendationControllers() {
    }

    @RestController
    @RequestMapping("/api/v1/public")
    @Tag(name = "Recommendations")
    @SecurityRequirements
    public static class PublicRecommendationController {

        private final RecommendationService recommendations;
        private final PromotionService promotions;
        private final ClientIpResolver ip;

        public PublicRecommendationController(RecommendationService recommendations, PromotionService promotions, ClientIpResolver ip) {
            this.recommendations = recommendations;
            this.promotions = promotions;
            this.ip = ip;
        }

        @GetMapping("/recommendations")
        @Operation(summary = "Offers worth knowing about near a point, chosen by AI when configured (location is not stored)")
        public ApiResponse<RecommendationService.Recommendations> recommend(@RequestParam double lat, @RequestParam double lng,
                                                                         @RequestParam(defaultValue = "8") double radiusKm,
                                                                         @RequestParam(required = false) String q,
                                                                         @RequestParam(required = false) java.util.List<String> interests,
                                                                         HttpServletRequest http) {
            String language = http.getLocale() == null ? "en" : http.getLocale().getLanguage();
            return ApiResponse.ok(recommendations.recommend(lat, lng, radiusKm, q, interests, language, ip.resolve(http)));
        }

        @GetMapping("/organizations/{slug}/promotions")
        @Operation(summary = "Current offers of a business")
        public ApiResponse<List<PromotionService.Promotion>> promotions(@PathVariable String slug) {
            return ApiResponse.ok(promotions.forPublic(slug));
        }
    }

    @RestController
    @RequestMapping("/api/v1/owner/organizations/{orgId}/promotions")
    @Tag(name = "Owner – Offers")
    public static class OwnerPromotionController {

        private final PromotionService promotions;

        public OwnerPromotionController(PromotionService promotions) {
            this.promotions = promotions;
        }

        @GetMapping
        @Operation(summary = "All offers of the business, including ended and upcoming ones")
        public ApiResponse<List<PromotionService.Promotion>> list(@PathVariable long orgId) {
            return ApiResponse.ok(promotions.forOwner(CurrentUser.require().id(), orgId));
        }

        @PostMapping
        @Operation(summary = "Publish an offer")
        public ApiResponse<List<PromotionService.Promotion>> add(@PathVariable long orgId, @RequestBody PromotionService.PromotionRequest req) {
            return ApiResponse.ok(promotions.add(CurrentUser.require().id(), orgId, req));
        }

        @DeleteMapping("/{promotionId}")
        @Operation(summary = "Remove an offer")
        public ApiResponse<List<PromotionService.Promotion>> delete(@PathVariable long orgId, @PathVariable long promotionId) {
            return ApiResponse.ok(promotions.delete(CurrentUser.require().id(), orgId, promotionId));
        }
    }
}
