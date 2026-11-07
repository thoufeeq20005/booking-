package com.bookeasy.organization.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.security.CurrentUser;
import com.bookeasy.common.web.ClientIpResolver;
import com.bookeasy.organization.dto.OrganizationDtos.OrganizationDetail;
import com.bookeasy.organization.dto.OrganizationDtos.OrganizationRequest;
import com.bookeasy.organization.service.OwnerOrganizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Entry point of business registration, open to every signed-in user (customers become business owners here).
 * Everything after this step lives under /api/v1/owner/**.
 */
@RestController
@RequestMapping("/api/v1/business/organizations")
@Tag(name = "Business registration")
public class BusinessRegistrationController {

    private final OwnerOrganizationService service;
    private final ClientIpResolver ip;

    public BusinessRegistrationController(OwnerOrganizationService service, ClientIpResolver ip) {
        this.service = service;
        this.ip = ip;
    }

    @PostMapping
    @Operation(summary = "Start a business application (creates a DRAFT and grants the BUSINESS_OWNER role)")
    public ApiResponse<OrganizationDetail> create(@Valid @RequestBody OrganizationRequest req, HttpServletRequest http) {
        return ApiResponse.ok(service.create(CurrentUser.require().id(), req, ip.resolve(http)));
    }
}
