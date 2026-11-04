package com.bookeasy.appointment;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.appointment.AppointmentRequests.BookBody;
import com.bookeasy.appointment.AppointmentRequests.ReasonBody;
import com.bookeasy.appointment.AppointmentRequests.RescheduleBody;
import com.bookeasy.appointment.BookingService.BookRequest;
import com.bookeasy.appointment.BookingService.CancelPreview;
import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** CUSTOMER only (SecurityConfig); every lookup is limited to the caller's own bookings. */
@RestController
@RequestMapping("/api/v1/customer/appointments")
@Tag(name = "Customer – Bookings")
public class CustomerAppointmentController {

    private final BookingService service;

    public CustomerAppointmentController(BookingService service) {
        this.service = service;
    }

    private static long me() {
        return CurrentUser.require().id();
    }

    @PostMapping
    @Operation(summary = "Book a slot returned by the public slots endpoint")
    public ApiResponse<AppointmentView> book(@Valid @RequestBody BookBody b) {
        return ApiResponse.ok(service.book(me(), new BookRequest(b.branchId(), b.serviceId(), b.staffId(), b.startsAt(), b.note())));
    }

    @GetMapping
    public ApiResponse<List<AppointmentView>> list() {
        return ApiResponse.ok(service.mine(me()));
    }

    @GetMapping("/{id}")
    public ApiResponse<AppointmentView> get(@PathVariable long id) {
        return ApiResponse.ok(service.mine(me(), id));
    }

    @GetMapping("/{id}/cancel-preview")
    @Operation(summary = "What a cancellation now would refund")
    public ApiResponse<CancelPreview> preview(@PathVariable long id) {
        return ApiResponse.ok(service.cancelPreview(me(), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<AppointmentView> cancel(@PathVariable long id, @Valid @RequestBody(required = false) ReasonBody b) {
        return ApiResponse.ok(service.cancel(me(), id, b == null ? null : b.reason()));
    }

    @PostMapping("/{id}/reschedule")
    public ApiResponse<AppointmentView> reschedule(@PathVariable long id, @Valid @RequestBody RescheduleBody b) {
        return ApiResponse.ok(service.reschedule(me(), id, b.startsAt(), b.staffId()));
    }
}
