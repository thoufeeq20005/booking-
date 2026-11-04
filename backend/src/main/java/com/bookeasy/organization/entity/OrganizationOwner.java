package com.bookeasy.organization.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "organization_owners")
@IdClass(OrganizationOwner.Key.class)
public class OrganizationOwner {

    public enum Role { PRIMARY, CO_OWNER }

    @Id
    @Column(name = "organization_id")
    private Long organizationId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrganizationOwner() {
    }

    public OrganizationOwner(long organizationId, long userId, Role role, Instant now) {
        this.organizationId = organizationId;
        this.userId = userId;
        this.role = role;
        this.createdAt = now;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    public static class Key implements Serializable {
        private Long organizationId;
        private Long userId;

        public Key() {
        }

        public Key(Long organizationId, Long userId) {
            this.organizationId = organizationId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(organizationId, k.organizationId)
                    && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(organizationId, userId);
        }
    }
}
