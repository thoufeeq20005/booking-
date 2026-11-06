package com.bookeasy.organization.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.bookeasy.category.dto.CategoryDtos.RequirementView;
import com.bookeasy.organization.entity.ConfirmationMode;
import com.bookeasy.organization.entity.OrganizationDocument;
import com.bookeasy.organization.entity.OrganizationOwner;
import com.bookeasy.organization.entity.OrganizationStatus;
import com.bookeasy.organization.entity.OrganizationVerification;

public final class OrganizationDtos {

    private OrganizationDtos() {
    }

    // =====================================================================================================
    // Requests
    // =====================================================================================================

    public record OrganizationRequest(
            @NotNull Long subcategoryId,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 2000) String description,
            @Email @Size(max = 255) String contactEmail,
            @Size(max = 20) String contactPhone,
            @Size(max = 255) String website,
            @Size(max = 15) String gstin,
            ConfirmationMode confirmationMode) {
    }

    /** Coordinates are limited to India's bounding box, which also catches swapped latitude/longitude. */
    public record BranchRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2,
            @Size(max = 120) String locality,
            @NotBlank @Size(max = 120) String city,
            @NotBlank @Size(max = 120) String state,
            @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$") String pincode,
            @NotNull @DecimalMin("6.0") @DecimalMax("37.6") BigDecimal latitude,
            @NotNull @DecimalMin("68.0") @DecimalMax("97.5") BigDecimal longitude,
            @Size(max = 20) String phone,
            @Size(max = 64) String timezone,
            Boolean active) {
    }

    public record DayHours(
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull LocalTime opensAt,
            @NotNull LocalTime closesAt,
            LocalTime breakStart,
            LocalTime breakEnd) {
    }

    /** The full weekly schedule; days not listed are closed. */
    public record HoursRequest(@NotNull @Size(max = 7) List<@Valid @NotNull DayHours> days) {
    }

    public record HolidayRequest(@NotNull LocalDate date, @Size(max = 200) String reason) {
    }

    public record DocumentReviewRequest(@NotNull OrganizationDocument.Status status, @Size(max = 1000) String note) {
    }

    /** Reason is required for reject and suspend, optional otherwise. */
    public record DecisionRequest(@Size(max = 1000) String reason) {
    }

    // =====================================================================================================
    // Views
    // =====================================================================================================

    public record OrganizationSummary(
            long id,
            String name,
            String slug,
            OrganizationStatus status,
            String statusReason,
            Long categoryId,
            Map<String, String> categoryName,
            long subcategoryId,
            Map<String, String> subcategoryName,
            String logoUrl,
            String coverUrl,
            String city,
            String ownerName,
            String ownerEmail,
            Instant submittedAt,
            Instant approvedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record DayHoursView(int dayOfWeek, LocalTime opensAt, LocalTime closesAt, LocalTime breakStart,
                               LocalTime breakEnd) {
    }

    public record BranchView(
            long id,
            String name,
            String addressLine1,
            String addressLine2,
            String locality,
            String city,
            String state,
            String pincode,
            BigDecimal latitude,
            BigDecimal longitude,
            String phone,
            String timezone,
            boolean active,
            List<DayHoursView> hours) {
    }

    public record HolidayView(long id, LocalDate date, String reason) {
    }

    public record DocumentView(long id, String fileName, String contentType, long sizeBytes,
                               OrganizationDocument.Status status, String reviewerNote, Instant uploadedAt,
                               Instant reviewedAt) {
    }

    /** One requirement of the category and the organization's upload for it, if any. */
    public record DocumentSlot(RequirementView requirement, DocumentView document) {
    }

    public record HistoryEntry(OrganizationVerification.Action action, OrganizationStatus fromStatus,
                               OrganizationStatus toStatus, String reason, Instant createdAt) {

        public static HistoryEntry of(OrganizationVerification v) {
            return new HistoryEntry(v.getAction(), v.getFromStatus(), v.getToStatus(), v.getReason(),
                    v.getCreatedAt());
        }
    }

    public record OwnerView(long userId, String fullName, String email, String phone, boolean emailVerified,
                            boolean phoneVerified, OrganizationOwner.Role role) {
    }

    public record Editable(boolean identity, boolean operations) {
    }

    /** Steps still missing before the application can be submitted. */
    public enum MissingStep { CONTACT, BRANCH, WORKING_HOURS, DOCUMENTS, ACCOUNT_VERIFICATION }

    public record OrganizationDetail(
            OrganizationSummary summary,
            String description,
            String contactEmail,
            String contactPhone,
            String website,
            String gstin,
            ConfirmationMode confirmationMode,
            Editable editable,
            List<MissingStep> missingSteps,
            List<BranchView> branches,
            List<DocumentSlot> documents,
            List<HistoryEntry> history,
            List<OwnerView> owners) {
    }
}
