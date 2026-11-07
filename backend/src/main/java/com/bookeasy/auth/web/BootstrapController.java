package com.bookeasy.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.auth.dto.AuthRequests;
import com.bookeasy.auth.dto.AuthResponses.UserView;
import com.bookeasy.auth.service.AdminBootstrapService;
import com.bookeasy.common.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/public/bootstrap")
@Tag(name = "Platform")
@SecurityRequirements
public class BootstrapController {

    private final AdminBootstrapService bootstrapService;
    private final AuthWebSupport web;

    public BootstrapController(AdminBootstrapService bootstrapService, AuthWebSupport web) {
        this.bootstrapService = bootstrapService;
        this.web = web;
    }

    @PostMapping("/super-admin")
    @Operation(summary = "One-time creation of the first Super Admin (see docs/admin-setup.md)")
    public ApiResponse<UserView> createSuperAdmin(@Valid @RequestBody AuthRequests.BootstrapAdmin req,
                                                  HttpServletRequest http) {
        return ApiResponse.ok(bootstrapService.createFirstSuperAdmin(req, web.context(http)));
    }
}
