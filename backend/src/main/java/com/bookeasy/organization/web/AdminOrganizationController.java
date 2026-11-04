package com.bookeasy.organization.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.api.PageResponse;
import com.bookeasy.common.security.CurrentUser;
import com.bookeasy.common.web.ClientIpResolver;
import com.bookeasy.file.FileResponses;
import com.bookeasy.organization.dto.OrganizationDtos.DecisionRequest;
import com.bookeasy.organization.dto.OrganizationDtos.DocumentReviewRequest;
import com.bookeasy.organization.dto.OrganizationDtos.OrganizationDetail;
import com.bookeasy.organization.dto.OrganizationDtos.OrganizationSummary;
import com.bookeasy.organization.entity.OrganizationStatus;
import com.bookeasy.organization.service.AdminOrganizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Business verification. SUPER_ADMIN only (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/organizations")
@Tag(name = "Admin – Organizations")
public class AdminOrganizationController {

    private final AdminOrganizationService service;
    private final FileResponses fileResponses;
    private final ClientIpResolver ip;

    public AdminOrganizationController(AdminOrganizationService service, FileResponses fileResponses,
                                       ClientIpResolver ip) {
        this.service = service;
        this.fileResponses = fileResponses;
        this.ip = ip;
    }

    @GetMapping
    @Operation(summary = "Search businesses; status=PENDING gives the review queue, oldest first")
    public ApiResponse<PageResponse<OrganizationSummary>> search(@RequestParam(required = false) String q,
                                                                 @RequestParam(required = false) OrganizationStatus status,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(q, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrganizationDetail> get(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @GetMapping("/{id}/documents/{documentId}/file")
    @Operation(summary = "View an uploaded verification document")
    public ResponseEntity<Resource> documentFile(@PathVariable long id, @PathVariable long documentId) {
        return fileResponses.privateFile(service.documentFile(id, documentId));
    }

    @PatchMapping("/{id}/documents/{documentId}")
    @Operation(summary = "Accept or reject one document (application must be pending)")
    public ApiResponse<OrganizationDetail> reviewDocument(@PathVariable long id, @PathVariable long documentId,
                                                          @Valid @RequestBody DocumentReviewRequest req,
                                                          HttpServletRequest http) {
        return ApiResponse.ok(service.reviewDocument(CurrentUser.require(), id, documentId, req, ip.resolve(http)));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<OrganizationDetail> approve(@PathVariable long id, @Valid @RequestBody(required = false)
                                                   DecisionRequest req, HttpServletRequest http) {
        return ApiResponse.ok(service.approve(CurrentUser.require(), id, reason(req), ip.resolve(http)));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<OrganizationDetail> reject(@PathVariable long id, @Valid @RequestBody DecisionRequest req,
                                                  HttpServletRequest http) {
        return ApiResponse.ok(service.reject(CurrentUser.require(), id, req.reason(), ip.resolve(http)));
    }

    @PostMapping("/{id}/suspend")
    public ApiResponse<OrganizationDetail> suspend(@PathVariable long id, @Valid @RequestBody DecisionRequest req,
                                                   HttpServletRequest http) {
        return ApiResponse.ok(service.suspend(CurrentUser.require(), id, req.reason(), ip.resolve(http)));
    }

    @PostMapping("/{id}/reinstate")
    public ApiResponse<OrganizationDetail> reinstate(@PathVariable long id, @Valid @RequestBody(required = false)
                                                     DecisionRequest req, HttpServletRequest http) {
        return ApiResponse.ok(service.reinstate(CurrentUser.require(), id, reason(req), ip.resolve(http)));
    }

    private static String reason(DecisionRequest req) {
        return req == null ? null : req.reason();
    }
}
