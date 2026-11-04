package com.bookeasy.organization.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.security.CurrentUser;
import com.bookeasy.common.web.ClientIpResolver;
import com.bookeasy.file.FileResponses;
import com.bookeasy.organization.dto.OrganizationDtos.BranchRequest;
import com.bookeasy.organization.dto.OrganizationDtos.BranchView;
import com.bookeasy.organization.dto.OrganizationDtos.DocumentSlot;
import com.bookeasy.organization.dto.OrganizationDtos.HolidayRequest;
import com.bookeasy.organization.dto.OrganizationDtos.HolidayView;
import com.bookeasy.organization.dto.OrganizationDtos.HoursRequest;
import com.bookeasy.organization.dto.OrganizationDtos.OrganizationDetail;
import com.bookeasy.organization.dto.OrganizationDtos.OrganizationRequest;
import com.bookeasy.organization.dto.OrganizationDtos.OrganizationSummary;
import com.bookeasy.organization.service.BranchService;
import com.bookeasy.organization.service.OrganizationDocumentService;
import com.bookeasy.organization.service.OwnerOrganizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The owner's businesses. BUSINESS_OWNER only (SecurityConfig); every service call also checks that the caller
 * owns the organization in the path.
 */
@RestController
@RequestMapping("/api/v1/owner/organizations")
@Tag(name = "Owner – Organizations")
public class OwnerOrganizationController {

    private final OwnerOrganizationService organizations;
    private final BranchService branches;
    private final OrganizationDocumentService documents;
    private final FileResponses fileResponses;
    private final ClientIpResolver ip;

    public OwnerOrganizationController(OwnerOrganizationService organizations, BranchService branches,
                                       OrganizationDocumentService documents, FileResponses fileResponses,
                                       ClientIpResolver ip) {
        this.organizations = organizations;
        this.branches = branches;
        this.documents = documents;
        this.fileResponses = fileResponses;
        this.ip = ip;
    }

    private static long me() {
        return CurrentUser.require().id();
    }

    @GetMapping
    @Operation(summary = "Businesses I own")
    public ApiResponse<List<OrganizationSummary>> list() {
        return ApiResponse.ok(organizations.listMine(me()));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrganizationDetail> get(@PathVariable long id) {
        return ApiResponse.ok(organizations.detail(me(), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update details (name and category only before approval)")
    public ApiResponse<OrganizationDetail> update(@PathVariable long id, @Valid @RequestBody OrganizationRequest req) {
        return ApiResponse.ok(organizations.update(me(), id, req));
    }

    @PostMapping(path = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a logo (PNG, JPEG or WebP, up to 2 MB; public)")
    public ApiResponse<OrganizationDetail> uploadLogo(@PathVariable long id, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(organizations.setLogo(me(), id, file));
    }

    @DeleteMapping("/{id}/logo")
    public ApiResponse<OrganizationDetail> removeLogo(@PathVariable long id) {
        return ApiResponse.ok(organizations.removeLogo(me(), id));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Send the application for review")
    public ApiResponse<OrganizationDetail> submit(@PathVariable long id, HttpServletRequest http) {
        return ApiResponse.ok(organizations.submit(me(), id, ip.resolve(http)));
    }

    // ----- branches -----

    @PostMapping("/{id}/branches")
    public ApiResponse<BranchView> createBranch(@PathVariable long id, @Valid @RequestBody BranchRequest req) {
        return ApiResponse.ok(branches.create(me(), id, req));
    }

    @PutMapping("/{id}/branches/{branchId}")
    public ApiResponse<BranchView> updateBranch(@PathVariable long id, @PathVariable long branchId,
                                                @Valid @RequestBody BranchRequest req) {
        return ApiResponse.ok(branches.update(me(), id, branchId, req));
    }

    @DeleteMapping("/{id}/branches/{branchId}")
    @Operation(summary = "Remove a branch (before approval; afterwards deactivate it instead)")
    public ApiResponse<Void> deleteBranch(@PathVariable long id, @PathVariable long branchId) {
        branches.delete(me(), id, branchId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/branches/{branchId}/hours")
    @Operation(summary = "Replace the weekly opening hours (days not listed are closed)")
    public ApiResponse<BranchView> setHours(@PathVariable long id, @PathVariable long branchId,
                                            @Valid @RequestBody HoursRequest req) {
        return ApiResponse.ok(branches.setHours(me(), id, branchId, req));
    }

    @GetMapping("/{id}/branches/{branchId}/holidays")
    @Operation(summary = "Upcoming holidays")
    public ApiResponse<List<HolidayView>> holidays(@PathVariable long id, @PathVariable long branchId) {
        return ApiResponse.ok(branches.holidays(me(), id, branchId));
    }

    @PostMapping("/{id}/branches/{branchId}/holidays")
    public ApiResponse<HolidayView> addHoliday(@PathVariable long id, @PathVariable long branchId,
                                               @Valid @RequestBody HolidayRequest req) {
        return ApiResponse.ok(branches.addHoliday(me(), id, branchId, req));
    }

    @DeleteMapping("/{id}/branches/{branchId}/holidays/{holidayId}")
    public ApiResponse<Void> deleteHoliday(@PathVariable long id, @PathVariable long branchId,
                                           @PathVariable long holidayId) {
        branches.deleteHoliday(me(), id, branchId, holidayId);
        return ApiResponse.ok(null);
    }

    // ----- verification documents -----

    @PostMapping(path = "/{id}/documents/{requirementId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace the document for a requirement (private)")
    public ApiResponse<DocumentSlot> uploadDocument(@PathVariable long id, @PathVariable long requirementId,
                                                    @RequestPart("file") MultipartFile file, HttpServletRequest http) {
        return ApiResponse.ok(documents.upload(me(), id, requirementId, file, ip.resolve(http)));
    }

    @DeleteMapping("/{id}/documents/{requirementId}")
    public ApiResponse<Void> deleteDocument(@PathVariable long id, @PathVariable long requirementId) {
        documents.delete(me(), id, requirementId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/documents/{requirementId}/file")
    @Operation(summary = "Download my uploaded document")
    public ResponseEntity<Resource> documentFile(@PathVariable long id, @PathVariable long requirementId) {
        return fileResponses.privateFile(documents.file(me(), id, requirementId));
    }
}
