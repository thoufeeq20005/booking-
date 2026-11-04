package com.bookeasy.discovery;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.discovery.DiscoveryService.PublicProfile;
import com.bookeasy.discovery.DiscoveryService.SearchPage;
import com.bookeasy.discovery.DiscoveryService.SearchQuery;
import com.bookeasy.discovery.DiscoveryService.Sort;
import com.bookeasy.discovery.DiscoveryService.Suggestion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Discovery")
@SecurityRequirements
@Validated
public class DiscoveryController {

    private final DiscoveryService service;

    public DiscoveryController(DiscoveryService service) {
        this.service = service;
    }

    @GetMapping("/search")
    @Operation(summary = "Approved businesses' branches near a point, filtered and sorted")
    public ApiResponse<SearchPage> search(@RequestParam @DecimalMin("6.0") @DecimalMax("37.6") double lat,
                                          @RequestParam @DecimalMin("68.0") @DecimalMax("97.5") double lng,
                                          @RequestParam(defaultValue = "10") double radiusKm,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) Long subcategoryId,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(required = false) BigDecimal minRating,
                                          @RequestParam(defaultValue = "DISTANCE") Sort sort,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(new SearchQuery(lat, lng, radiusKm, categoryId, subcategoryId,
                q == null ? null : q.substring(0, Math.min(q.length(), 100)), minRating, sort, page, size)));
    }

    @GetMapping("/suggest")
    public ApiResponse<List<Suggestion>> suggest(@RequestParam String q) {
        return ApiResponse.ok(service.suggest(q.substring(0, Math.min(q.length(), 100))));
    }

    @GetMapping("/organizations/{slug}")
    @Operation(summary = "Public profile of an approved business: branches, hours, services, staff")
    public ApiResponse<PublicProfile> profile(@PathVariable String slug) {
        return ApiResponse.ok(service.profile(slug));
    }
}
