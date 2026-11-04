package com.bookeasy.organization.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The current upload for one document requirement of an organization. */
@Entity
@Table(name = "organization_documents")
public class OrganizationDocument {

    public enum Status { SUBMITTED, ACCEPTED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "requirement_id", nullable = false, updatable = false)
    private Long requirementId;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.SUBMITTED;

    @Column(name = "reviewer_note", length = 1000)
    private String reviewerNote;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizationDocument() {
    }

    public OrganizationDocument(long organizationId, long requirementId, long fileId, Instant now) {
        this.organizationId = organizationId;
        this.requirementId = requirementId;
        this.fileId = fileId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A new upload replaces the file and clears any earlier review. */
    public void replaceFile(long fileId, Instant now) {
        this.fileId = fileId;
        this.status = Status.SUBMITTED;
        this.reviewerNote = null;
        this.reviewedBy = null;
        this.reviewedAt = null;
        this.updatedAt = now;
    }

    public void review(Status status, String note, long reviewerId, Instant now) {
        this.status = status;
        this.reviewerNote = note;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getRequirementId() {
        return requirementId;
    }

    public Long getFileId() {
        return fileId;
    }

    public Status getStatus() {
        return status;
    }

    public String getReviewerNote() {
        return reviewerNote;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
