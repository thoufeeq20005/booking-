package com.bookeasy.file;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "uploaded_files")
public class UploadedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 191)
    private String storageKey;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, columnDefinition = "CHAR(64)")
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FilePurpose purpose;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UploadedFile() {
    }

    UploadedFile(String publicId, String storageKey, String originalName, String contentType, long sizeBytes,
                 String sha256, FileVisibility visibility, FilePurpose purpose, Long ownerUserId, Instant createdAt) {
        this.publicId = publicId;
        this.storageKey = storageKey;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.visibility = visibility;
        this.purpose = purpose;
        this.ownerUserId = ownerUserId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public FileVisibility getVisibility() {
        return visibility;
    }

    public FilePurpose getPurpose() {
        return purpose;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Path of the public download endpoint, or null for private files. */
    public String publicUrl() {
        return visibility == FileVisibility.PUBLIC ? "/api/v1/public/files/" + publicId : null;
    }
}
