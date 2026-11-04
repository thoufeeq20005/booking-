package com.bookeasy.organization.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subcategory_id", nullable = false)
    private Long subcategoryId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 160, updatable = false)
    private String slug;

    @Column(length = 2000)
    private String description;

    @Column(name = "logo_file_id")
    private Long logoFileId;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone", length = 16)
    private String contactPhone;

    @Column(length = 255)
    private String website;

    @Column(columnDefinition = "CHAR(15)")
    private String gstin;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 16)
    private OrganizationStatus status = OrganizationStatus.DRAFT;

    @Column(name = "status_reason", length = 1000)
    private String statusReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_mode", nullable = false, length = 16)
    private ConfirmationMode confirmationMode = ConfirmationMode.INSTANT;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO.setScale(2);

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Organization() {
    }

    public Organization(String slug, long createdBy, Instant now) {
        this.slug = slug;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ----- lifecycle -----

    public void submit(Instant now) {
        require(status == OrganizationStatus.DRAFT || status == OrganizationStatus.REJECTED);
        status = OrganizationStatus.PENDING;
        submittedAt = now;
        updatedAt = now;
    }

    public void approve(Instant now) {
        require(status == OrganizationStatus.PENDING);
        status = OrganizationStatus.APPROVED;
        statusReason = null;
        if (approvedAt == null) {
            approvedAt = now;
        }
        updatedAt = now;
    }

    public void reject(String reason, Instant now) {
        require(status == OrganizationStatus.PENDING);
        status = OrganizationStatus.REJECTED;
        statusReason = reason;
        updatedAt = now;
    }

    public void suspend(String reason, Instant now) {
        require(status == OrganizationStatus.APPROVED);
        status = OrganizationStatus.SUSPENDED;
        statusReason = reason;
        updatedAt = now;
    }

    public void reinstate(Instant now) {
        require(status == OrganizationStatus.SUSPENDED);
        status = OrganizationStatus.APPROVED;
        statusReason = null;
        updatedAt = now;
    }

    private static void require(boolean allowed) {
        if (!allowed) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    // ----- edits (callers check status first) -----

    public void updateIdentity(long subcategoryId, String name) {
        this.subcategoryId = subcategoryId;
        this.name = name;
    }

    public void updateOperations(String description, String contactEmail, String contactPhone, String website,
                                 String gstin, ConfirmationMode confirmationMode, Instant now) {
        this.description = description;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.website = website;
        this.gstin = gstin;
        this.confirmationMode = confirmationMode;
        this.updatedAt = now;
    }

    public void setLogoFileId(Long logoFileId, Instant now) {
        this.logoFileId = logoFileId;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    // ----- getters -----

    public Long getId() {
        return id;
    }

    public Long getSubcategoryId() {
        return subcategoryId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public Long getLogoFileId() {
        return logoFileId;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getWebsite() {
        return website;
    }

    public String getGstin() {
        return gstin;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public ConfirmationMode getConfirmationMode() {
        return confirmationMode;
    }

    public BigDecimal getRatingAvg() {
        return ratingAvg;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
