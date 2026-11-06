package com.bookeasy.staff;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.MediaType;
import java.time.LocalDate;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.availability.SlotService;
import com.bookeasy.availability.SlotService.DaySlots;
import com.bookeasy.catalog.CatalogDtos.ServiceRequest;
import com.bookeasy.catalog.CatalogDtos.ServiceView;
import com.bookeasy.catalog.CatalogService;
import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.security.CurrentUser;
import com.bookeasy.common.web.ClientIpResolver;
import com.bookeasy.staff.StaffDtos.AvailabilityRequest;
import com.bookeasy.staff.StaffDtos.InviteRequest;
import com.bookeasy.staff.StaffDtos.LeaveRequest;
import com.bookeasy.staff.StaffDtos.StaffRequest;
import com.bookeasy.staff.StaffDtos.StaffView;
import com.bookeasy.staff.StaffDtos.StatusRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** An owner's services, staff and slot preview. BUSINESS_OWNER only; every call checks ownership. */
@RestController
@RequestMapping("/api/v1/owner/organizations/{orgId}")
@Tag(name = "Owner – Services & staff")
public class OwnerTeamController {

    private final CatalogService catalog;
    private final StaffService staff;
    private final SlotService slots;
    private final ClientIpResolver ip;

    public OwnerTeamController(CatalogService catalog, StaffService staff, SlotService slots, ClientIpResolver ip) {
        this.catalog = catalog;
        this.staff = staff;
        this.slots = slots;
        this.ip = ip;
    }

    private static long me() {
        return CurrentUser.require().id();
    }

    // ----- services -----

    @GetMapping("/services")
    public ApiResponse<List<ServiceView>> services(@PathVariable long orgId) {
        return ApiResponse.ok(catalog.list(me(), orgId));
    }

    @PostMapping("/services")
    public ApiResponse<ServiceView> createService(@PathVariable long orgId, @Valid @RequestBody ServiceRequest req) {
        return ApiResponse.ok(catalog.create(me(), orgId, req));
    }

    @PutMapping("/services/{serviceId}")
    public ApiResponse<ServiceView> updateService(@PathVariable long orgId, @PathVariable long serviceId,
                                                  @Valid @RequestBody ServiceRequest req) {
        return ApiResponse.ok(catalog.update(me(), orgId, serviceId, req));
    }

    @DeleteMapping("/services/{serviceId}")
    public ApiResponse<Void> deleteService(@PathVariable long orgId, @PathVariable long serviceId) {
        catalog.delete(me(), orgId, serviceId);
        return ApiResponse.ok(null);
    }

    @PostMapping(path = "/services/{serviceId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a service photo (PNG, JPEG or WebP, up to 5 MB; public)")
    public ApiResponse<ServiceView> uploadServiceImage(@PathVariable long orgId, @PathVariable long serviceId,
                                                       @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(catalog.setImage(me(), orgId, serviceId, file));
    }

    @DeleteMapping("/services/{serviceId}/image")
    public ApiResponse<ServiceView> removeServiceImage(@PathVariable long orgId, @PathVariable long serviceId) {
        return ApiResponse.ok(catalog.removeImage(me(), orgId, serviceId));
    }

    // ----- staff -----

    @GetMapping("/staff")
    public ApiResponse<List<StaffView>> staff(@PathVariable long orgId) {
        return ApiResponse.ok(staff.list(me(), orgId));
    }

    @PostMapping("/staff/invitations")
    @Operation(summary = "Invite someone by email (link valid 7 days)")
    public ApiResponse<StaffView> invite(@PathVariable long orgId, @Valid @RequestBody InviteRequest req,
                                         HttpServletRequest http) {
        return ApiResponse.ok(staff.invite(me(), orgId, req, ip.resolve(http)));
    }

    @PostMapping("/staff/self")
    @Operation(summary = "Add myself as a staff member (solo practitioners)")
    public ApiResponse<StaffView> addSelf(@PathVariable long orgId, @Valid @RequestBody StaffRequest req,
                                          HttpServletRequest http) {
        return ApiResponse.ok(staff.addSelf(me(), orgId, req, ip.resolve(http)));
    }

    @PutMapping("/staff/{staffId}")
    public ApiResponse<StaffView> updateStaff(@PathVariable long orgId, @PathVariable long staffId,
                                              @Valid @RequestBody StaffRequest req, HttpServletRequest http) {
        return ApiResponse.ok(staff.update(me(), orgId, staffId, req, ip.resolve(http)));
    }

    @PostMapping("/staff/{staffId}/status")
    public ApiResponse<StaffView> setStatus(@PathVariable long orgId, @PathVariable long staffId,
                                            @Valid @RequestBody StatusRequest req, HttpServletRequest http) {
        return ApiResponse.ok(staff.setStatus(me(), orgId, staffId, req.status(), ip.resolve(http)));
    }

    @PostMapping("/staff/{staffId}/invitation/resend")
    public ApiResponse<StaffView> resend(@PathVariable long orgId, @PathVariable long staffId) {
        return ApiResponse.ok(staff.resendInvitation(me(), orgId, staffId));
    }

    @DeleteMapping("/staff/{staffId}/invitation")
    public ApiResponse<Void> revoke(@PathVariable long orgId, @PathVariable long staffId, HttpServletRequest http) {
        staff.revokeInvitation(me(), orgId, staffId, ip.resolve(http));
        return ApiResponse.ok(null);
    }

    @PutMapping("/staff/{staffId}/availability")
    @Operation(summary = "Replace the weekly availability (branch-local times)")
    public ApiResponse<StaffView> availability(@PathVariable long orgId, @PathVariable long staffId,
                                               @Valid @RequestBody AvailabilityRequest req) {
        return ApiResponse.ok(staff.setAvailability(me(), orgId, staffId, req));
    }

    @PostMapping("/staff/{staffId}/leaves")
    public ApiResponse<StaffView> addLeave(@PathVariable long orgId, @PathVariable long staffId,
                                           @Valid @RequestBody LeaveRequest req) {
        return ApiResponse.ok(staff.addLeave(me(), orgId, staffId, req));
    }

    @DeleteMapping("/staff/{staffId}/leaves/{leaveId}")
    public ApiResponse<StaffView> deleteLeave(@PathVariable long orgId, @PathVariable long staffId,
                                              @PathVariable long leaveId) {
        return ApiResponse.ok(staff.deleteLeave(me(), orgId, staffId, leaveId));
    }

    // ----- slot preview -----

    @GetMapping("/slots")
    @Operation(summary = "Preview the slots customers would see on a date (works before approval)")
    public ApiResponse<DaySlots> slots(@PathVariable long orgId, @RequestParam long branchId,
                                       @RequestParam long serviceId,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @RequestParam(required = false) Long staffId) {
        return ApiResponse.ok(slots.ownerPreview(me(), orgId, branchId, serviceId, date, staffId));
    }
}
