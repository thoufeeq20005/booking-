package com.bookeasy.staff;

import java.time.LocalDate;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.availability.SlotService;
import com.bookeasy.availability.SlotService.DaySlots;
import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.security.CurrentUser;
import com.bookeasy.common.web.ClientIpResolver;
import com.bookeasy.staff.StaffDtos.AvailabilityRequest;
import com.bookeasy.staff.StaffDtos.InvitationPreview;
import com.bookeasy.staff.StaffDtos.LeaveRequest;
import com.bookeasy.staff.StaffDtos.StaffView;
import com.bookeasy.staff.StaffDtos.TokenRequest;
import com.bookeasy.staff.StaffDtos.Workplace;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Invitations, the staff member's own area, and the public slot endpoint. */
@RestController
@Tag(name = "Staff")
public class StaffSelfController {

    private final StaffService staff;
    private final SlotService slots;
    private final ClientIpResolver ip;

    public StaffSelfController(StaffService staff, SlotService slots, ClientIpResolver ip) {
        this.staff = staff;
        this.slots = slots;
        this.ip = ip;
    }

    @PostMapping("/api/v1/public/staff-invitations/preview")
    @Operation(summary = "Who invited me? (token in the body so it stays out of logs)")
    public ApiResponse<InvitationPreview> preview(@Valid @RequestBody TokenRequest req) {
        return ApiResponse.ok(staff.preview(req.token()));
    }

    @PostMapping("/api/v1/staff-invitations/accept")
    @Operation(summary = "Join the business; the signed-in account must use the invited email")
    public ApiResponse<Workplace> accept(@Valid @RequestBody TokenRequest req, HttpServletRequest http) {
        return ApiResponse.ok(staff.accept(CurrentUser.require().id(), req.token(), ip.resolve(http)));
    }

    @GetMapping("/api/v1/staff/workplaces")
    public ApiResponse<List<Workplace>> workplaces() {
        return ApiResponse.ok(staff.myWorkplaces(CurrentUser.require().id()));
    }

    @PutMapping("/api/v1/staff/profiles/{staffId}/availability")
    @Operation(summary = "Edit my own availability (needs CAN_EDIT_HOURS)")
    public ApiResponse<StaffView> availability(@PathVariable long staffId, @Valid @RequestBody AvailabilityRequest req) {
        return ApiResponse.ok(staff.setOwnAvailability(CurrentUser.require().id(), staffId, req));
    }

    @PostMapping("/api/v1/staff/profiles/{staffId}/leaves")
    @Operation(summary = "Add my own leave (needs CAN_EDIT_HOURS)")
    public ApiResponse<StaffView> leave(@PathVariable long staffId, @Valid @RequestBody LeaveRequest req) {
        return ApiResponse.ok(staff.addOwnLeave(CurrentUser.require().id(), staffId, req));
    }

    @GetMapping("/api/v1/public/branches/{branchId}/services/{serviceId}/slots")
    @Operation(summary = "Free slots on a date at an approved business")
    public ApiResponse<DaySlots> publicSlots(@PathVariable long branchId, @PathVariable long serviceId,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                             @RequestParam(required = false) Long staffId) {
        return ApiResponse.ok(slots.publicSlots(branchId, serviceId, date, staffId));
    }
}
