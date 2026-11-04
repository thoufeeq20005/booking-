package com.bookeasy.customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.security.CurrentUser;
import com.bookeasy.customer.CustomerExtrasService.FamilyMember;
import com.bookeasy.customer.CustomerExtrasService.Favourite;
import com.bookeasy.customer.CustomerExtrasService.Relation;
import com.bookeasy.customer.CustomerExtrasService.SavedLocation;
import com.bookeasy.customer.CustomerExtrasService.SupportRequest;
import com.bookeasy.customer.CustomerExtrasService.SupportStatus;
import com.bookeasy.customer.RetentionService.Run;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Customer extras & support")
public class CustomerExtrasController {

    private final CustomerExtrasService service;
    private final RetentionService retention;

    public CustomerExtrasController(CustomerExtrasService service, RetentionService retention) {
        this.service = service;
        this.retention = retention;
    }

    public record FamilyBody(@NotBlank @Size(max = 120) String fullName, @NotNull Relation relation, LocalDate dateOfBirth) {
    }

    public record LocationBody(@NotBlank @Size(max = 60) String label,
                               @NotNull @DecimalMin("6.0") @DecimalMax("37.6") BigDecimal latitude,
                               @NotNull @DecimalMin("68.0") @DecimalMax("97.5") BigDecimal longitude) {
    }

    public record SupportBody(Long appointmentId, @NotBlank @Size(max = 150) String subject, @NotBlank @Size(max = 4000) String message) {
    }

    public record AnswerBody(@NotNull SupportStatus status, @Size(max = 4000) String reply) {
    }

    private static long me() {
        return CurrentUser.require().id();
    }

    @GetMapping("/api/v1/customer/family")
    public ApiResponse<List<FamilyMember>> family() {
        return ApiResponse.ok(service.family(me()));
    }

    @PostMapping("/api/v1/customer/family")
    public ApiResponse<FamilyMember> addFamily(@Valid @RequestBody FamilyBody b) {
        return ApiResponse.ok(service.addFamily(me(), b.fullName(), b.relation(), b.dateOfBirth()));
    }

    @DeleteMapping("/api/v1/customer/family/{id}")
    public ApiResponse<Void> removeFamily(@PathVariable long id) {
        service.removeFamily(me(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/v1/customer/favourites")
    public ApiResponse<List<Favourite>> favourites() {
        return ApiResponse.ok(service.favourites(me()));
    }

    @PutMapping("/api/v1/customer/favourites/{orgId}")
    public ApiResponse<Void> addFavourite(@PathVariable long orgId) {
        service.addFavourite(me(), orgId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/v1/customer/favourites/{orgId}")
    public ApiResponse<Void> removeFavourite(@PathVariable long orgId) {
        service.removeFavourite(me(), orgId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/v1/customer/saved-locations")
    public ApiResponse<List<SavedLocation>> locations() {
        return ApiResponse.ok(service.locations(me()));
    }

    @PostMapping("/api/v1/customer/saved-locations")
    public ApiResponse<List<SavedLocation>> saveLocation(@Valid @RequestBody LocationBody b) {
        return ApiResponse.ok(service.saveLocation(me(), b.label(), b.latitude(), b.longitude()));
    }

    @DeleteMapping("/api/v1/customer/saved-locations/{id}")
    public ApiResponse<Void> removeLocation(@PathVariable long id) {
        service.removeLocation(me(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/v1/support-requests")
    @Operation(summary = "Contact support (any signed-in user, at most 5 per hour)")
    public ApiResponse<SupportRequest> open(@Valid @RequestBody SupportBody b) {
        return ApiResponse.ok(service.openRequest(me(), b.appointmentId(), b.subject(), b.message()));
    }

    @GetMapping("/api/v1/support-requests")
    public ApiResponse<List<SupportRequest>> mine() {
        return ApiResponse.ok(service.myRequests(me()));
    }

    @GetMapping("/api/v1/admin/support-requests")
    public ApiResponse<List<SupportRequest>> adminList(@RequestParam(required = false) SupportStatus status) {
        return ApiResponse.ok(service.adminRequests(status));
    }

    @PostMapping("/api/v1/admin/support-requests/{id}")
    public ApiResponse<SupportRequest> answer(@PathVariable long id, @Valid @RequestBody AnswerBody b) {
        return ApiResponse.ok(service.answer(id, b.status(), b.reply()));
    }

    @PostMapping("/api/v1/admin/retention/run")
    @Operation(summary = "Run data retention now (normally nightly at 03:30 IST)")
    public ApiResponse<Run> runRetention() {
        return ApiResponse.ok(retention.run());
    }

    @GetMapping("/api/v1/admin/retention/runs")
    public ApiResponse<List<Run>> retentionRuns() {
        return ApiResponse.ok(retention.runs());
    }
}
