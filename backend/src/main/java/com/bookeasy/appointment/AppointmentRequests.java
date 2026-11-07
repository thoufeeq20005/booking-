package com.bookeasy.appointment;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies of the booking endpoints. */
public final class AppointmentRequests {

    private AppointmentRequests() {
    }

    public record BookBody(@NotNull Long branchId, @NotNull Long serviceId, Long staffId, @NotNull Instant startsAt,
                           @Size(max = 500) String note, Long familyMemberId) {
    }

    public record ReasonBody(@Size(max = 500) String reason) {
    }

    public record RescheduleBody(@NotNull Instant startsAt, Long staffId) {
    }

    public record CodeBody(@NotBlank @Size(max = 200) String code) {
    }
}
