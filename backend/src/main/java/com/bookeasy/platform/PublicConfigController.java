package com.bookeasy.platform;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.platform.dto.PublicConfigResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/public/config")
@Tag(name = "Platform")
@SecurityRequirements
public class PublicConfigController {

    private final PlatformConfigService platformConfigService;

    public PublicConfigController(PlatformConfigService platformConfigService) {
        this.platformConfigService = platformConfigService;
    }

    @GetMapping
    @Operation(summary = "Public platform configuration (currency, commission, languages, support contacts)")
    public ApiResponse<PublicConfigResponse> get() {
        return ApiResponse.ok(platformConfigService.publicConfig());
    }
}
