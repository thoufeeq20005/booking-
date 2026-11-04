package com.bookeasy.appointment;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.appointment.AppointmentRequests.CodeBody;
import com.bookeasy.appointment.AppointmentRequests.ReasonBody;
import com.bookeasy.appointment.BusinessAppointmentService.Action;
import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Owner (/owner/**) and staff (/staff/**) booking views and actions. */
@RestController
@Tag(name = "Business – Bookings")
public class BusinessAppointmentController {

    private final BusinessAppointmentService service;

    public BusinessAppointmentController(BusinessAppointmentService service) {
        this.service = service;
    }

    private static long me() {
        return CurrentUser.require().id();
    }

    @GetMapping("/api/v1/owner/organizations/{orgId}/appointments")
    public ApiResponse<List<AppointmentView>> ownerDay(@PathVariable long orgId,
                                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                       @RequestParam(required = false) Long branchId) {
        return ApiResponse.ok(service.ownerDay(me(), orgId, date, branchId));
    }

    @PostMapping("/api/v1/owner/organizations/{orgId}/appointments/{id}/{action}")
    @Operation(summary = "confirm | decline | cancel | check-in | complete | no-show (decline and cancel need a reason)")
    public ApiResponse<AppointmentView> ownerAct(@PathVariable long orgId, @PathVariable long id, @PathVariable String action,
                                                 @Valid @RequestBody(required = false) ReasonBody b) {
        return ApiResponse.ok(service.ownerAct(me(), orgId, id, parse(action), b == null ? null : b.reason()));
    }

    @PostMapping("/api/v1/owner/organizations/{orgId}/check-in")
    @Operation(summary = "Check a customer in with their booking pass")
    public ApiResponse<AppointmentView> scan(@PathVariable long orgId, @Valid @RequestBody CodeBody b) {
        return ApiResponse.ok(service.ownerCheckIn(me(), orgId, b.code()));
    }

    @GetMapping("/api/v1/staff/appointments")
    public ApiResponse<List<AppointmentView>> staffDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(service.staffDay(me(), date));
    }

    @PostMapping("/api/v1/staff/appointments/{id}/{action}")
    public ApiResponse<AppointmentView> staffAct(@PathVariable long id, @PathVariable String action,
                                                 @Valid @RequestBody(required = false) ReasonBody b) {
        return ApiResponse.ok(service.staffAct(me(), id, parse(action), b == null ? null : b.reason()));
    }

    private static Action parse(String action) {
        try {
            return Action.valueOf(action.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Action");
        }
    }
}
