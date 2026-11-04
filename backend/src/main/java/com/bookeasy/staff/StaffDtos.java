package com.bookeasy.staff;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class StaffDtos {

    private StaffDtos() {
    }

    public record StaffRequest(
            @NotBlank @Size(max = 120) String displayName,
            @Size(max = 40) String title,
            @Size(max = 150) String specialisation,
            Set<StaffProfile.Permission> permissions,
            @NotNull @Size(max = 50) Set<Long> branchIds,
            @NotNull @Size(max = 200) Set<Long> serviceIds) {
    }

    public record InviteRequest(@NotBlank @Email @Size(max = 255) String email, @NotNull @Valid StaffRequest profile) {
    }

    public record StatusRequest(@NotNull StaffProfile.Status status) {
    }

    public record WindowDto(@NotNull Long branchId, @Min(1) @Max(7) int dayOfWeek, @NotNull LocalTime startTime,
                            @NotNull LocalTime endTime) {
    }

    public record AvailabilityRequest(@NotNull @Size(max = 100) List<@Valid @NotNull WindowDto> windows) {
    }

    public record LeaveRequest(@NotNull Instant startsAt, @NotNull Instant endsAt, @Size(max = 200) String reason) {
    }

    public record LeaveView(long id, Instant startsAt, Instant endsAt, String reason) {
    }

    public record InvitationView(String email, Instant expiresAt, boolean open) {
    }

    public record StaffView(long id, Long userId, String displayName, String title, String specialisation,
                            Set<StaffProfile.Permission> permissions, StaffProfile.Status status, Set<Long> branchIds,
                            Set<Long> serviceIds, String email, InvitationView invitation, List<WindowDto> availability,
                            List<LeaveView> upcomingLeave) {
    }

    /** A staff member's own view of one workplace. */
    public record Workplace(long organizationId, String organizationName, StaffView profile,
                            List<Named> branches, List<Named> services) {
    }

    public record Named(long id, String name) {
    }

    public record InvitationPreview(String organizationName, String displayName, String email, Instant expiresAt) {
    }

    public record TokenRequest(@NotBlank @Size(max = 200) String token) {
    }
}
