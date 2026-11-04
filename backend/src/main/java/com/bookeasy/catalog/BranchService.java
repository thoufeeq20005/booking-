package com.bookeasy.catalog;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** A branch offering a service, optionally at its own price. */
@Entity
@Table(name = "branch_services")
@IdClass(BranchService.Key.class)
public class BranchService {

    @Id
    @Column(name = "branch_id")
    private Long branchId;

    @Id
    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "price_override", precision = 12, scale = 2)
    private BigDecimal priceOverride;

    protected BranchService() {
    }

    public BranchService(long branchId, long serviceId, BigDecimal priceOverride) {
        this.branchId = branchId;
        this.serviceId = serviceId;
        this.priceOverride = priceOverride;
    }

    public Long getBranchId() { return branchId; }
    public Long getServiceId() { return serviceId; }
    public BigDecimal getPriceOverride() { return priceOverride; }

    public static class Key implements Serializable {
        private Long branchId;
        private Long serviceId;

        public Key() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(branchId, k.branchId) && Objects.equals(serviceId, k.serviceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(branchId, serviceId);
        }
    }
}
