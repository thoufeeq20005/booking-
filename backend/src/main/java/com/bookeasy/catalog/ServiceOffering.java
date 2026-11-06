package com.bookeasy.catalog;

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

/** A bookable service of an organization (table {@code services}). */
@Entity
@Table(name = "services")
public class ServiceOffering {

    public enum ConsultationType { IN_PERSON, ONLINE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "consultation_type", nullable = false, length = 16)
    private ConsultationType consultationType = ConsultationType.IN_PERSON;

    @Column(name = "requires_staff", nullable = false)
    private boolean requiresStaff = true;

    @Column(name = "buffer_minutes", nullable = false)
    private int bufferMinutes;

    @Column(name = "slot_interval_minutes", nullable = false)
    private int slotIntervalMinutes = 15;

    @Column(name = "max_advance_days", nullable = false)
    private int maxAdvanceDays = 60;

    @Column(name = "min_notice_minutes", nullable = false)
    private int minNoticeMinutes = 60;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "image_file_id")
    private Long imageFileId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ServiceOffering() {
    }

    public ServiceOffering(long organizationId, Instant now) {
        this.organizationId = organizationId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String description, int durationMinutes, BigDecimal price,
                       ConsultationType type, boolean requiresStaff, int bufferMinutes, int slotIntervalMinutes,
                       int maxAdvanceDays, int minNoticeMinutes, boolean active, Instant now) {
        this.name = name;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.price = price;
        this.consultationType = type;
        this.requiresStaff = requiresStaff;
        this.bufferMinutes = bufferMinutes;
        this.slotIntervalMinutes = slotIntervalMinutes;
        this.maxAdvanceDays = maxAdvanceDays;
        this.minNoticeMinutes = minNoticeMinutes;
        this.active = active;
        this.updatedAt = now;
    }

    public void setImageFileId(Long imageFileId, Instant now) {
        this.imageFileId = imageFileId;
        this.updatedAt = now;
    }

    public Long getImageFileId() { return imageFileId; }
    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getDurationMinutes() { return durationMinutes; }
    public BigDecimal getPrice() { return price; }
    public ConsultationType getConsultationType() { return consultationType; }
    public boolean isRequiresStaff() { return requiresStaff; }
    public int getBufferMinutes() { return bufferMinutes; }
    public int getSlotIntervalMinutes() { return slotIntervalMinutes; }
    public int getMaxAdvanceDays() { return maxAdvanceDays; }
    public int getMinNoticeMinutes() { return minNoticeMinutes; }
    public boolean isActive() { return active; }
}
