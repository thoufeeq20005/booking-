package com.bookeasy.appointment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

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

/**
 * A booking. Status machine:
 * <pre>
 * PENDING_PAYMENT ─paid─▶ PENDING_APPROVAL ─confirm─▶ CONFIRMED ─complete─▶ COMPLETED
 *        │ (or straight to CONFIRMED for instant businesses)       ├─no-show─▶ NO_SHOW
 *        └─timeout─▶ EXPIRED          PENDING_APPROVAL ─decline─▶ DECLINED
 * any active state ─cancel─▶ CANCELLED
 * </pre>
 */
@Entity
@Table(name = "appointments")
public class Appointment {

    public enum Status {
        PENDING_PAYMENT, PENDING_APPROVAL, CONFIRMED, CANCELLED, DECLINED, COMPLETED, NO_SHOW, EXPIRED;

        /** States that hold the time slot. */
        public static final Set<Status> ACTIVE = EnumSet.of(PENDING_PAYMENT, PENDING_APPROVAL, CONFIRMED);
    }

    public enum Party { CUSTOMER, BUSINESS, SYSTEM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 12, updatable = false)
    private String reference;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "service_id", nullable = false, updatable = false)
    private Long serviceId;

    @Column(name = "staff_id")
    private Long staffId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "blocked_until", nullable = false)
    private Instant blockedUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "service_name", nullable = false, length = 150, updatable = false)
    private String serviceName;

    @Column(name = "service_price", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal servicePrice;

    @Column(name = "platform_fee", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal platformFee;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "customer_note", length = 500)
    private String customerNote;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = 16)
    private Party cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "family_member_id")
    private Long familyMemberId;

    @Column(name = "patient_name", length = 120)
    private String patientName;

    /** Movie ticket bookings: the show; the seats are in ticket_seats. */
    @Column(name = "showtime_id")
    private Long showtimeId;

    /** Table bookings: number of guests. */
    @Column(name = "party_size")
    private Integer partySize;

    /** Table bookings: the offer chosen for the final bill. Bill payments: the offer applied. */
    @Column(name = "offer_title", length = 80)
    private String offerTitle;

    @Column(name = "offer_percent")
    private Integer offerPercent;

    /** Bill payments only: the bill before the offer and cover credit; service_price is what is paid now. */
    @Column(name = "bill_amount")
    private BigDecimal billAmount;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    @Column(name = "cover_credit")
    private BigDecimal coverCredit;

    @Column(name = "bill_for_appointment_id")
    private Long billForAppointmentId;

    @Column(name = "reschedule_count", nullable = false)
    private int rescheduleCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Appointment() {
    }

    public Appointment(String reference, long customerId, long organizationId, long branchId, long serviceId, Long staffId,
                       Instant startsAt, Instant endsAt, Instant blockedUntil, Status status, String serviceName,
                       BigDecimal servicePrice, BigDecimal platformFee, String customerNote, Instant now) {
        this.reference = reference;
        this.customerId = customerId;
        this.organizationId = organizationId;
        this.branchId = branchId;
        this.serviceId = serviceId;
        this.staffId = staffId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.blockedUntil = blockedUntil;
        this.status = status;
        this.serviceName = serviceName;
        this.servicePrice = servicePrice;
        this.platformFee = platformFee;
        this.totalAmount = servicePrice.add(platformFee);
        this.customerNote = customerNote;
        this.createdAt = now;
        this.updatedAt = now;
    }

    private void require(boolean ok) {
        if (!ok) {
            throw new ApiException(ErrorCode.BOOKING_NOT_CHANGEABLE);
        }
    }

    /** Booked by the account holder for a family member; the name is kept even if the member is later removed. */
    public void forShowtime(long showtimeId) {
        this.showtimeId = showtimeId;
    }

    public void forParty(int partySize) {
        this.partySize = partySize;
    }

    public Long getShowtimeId() { return showtimeId; }

    public void withOffer(String title, Integer percent) {
        this.offerTitle = title;
        this.offerPercent = percent;
    }

    public void asBill(BigDecimal billAmount, BigDecimal discountAmount, BigDecimal coverCredit, Long forBooking) {
        this.billAmount = billAmount;
        this.discountAmount = discountAmount;
        this.coverCredit = coverCredit;
        this.billForAppointmentId = forBooking;
    }

    public String getOfferTitle() { return offerTitle; }

    public Integer getOfferPercent() { return offerPercent; }

    public BigDecimal getBillAmount() { return billAmount; }

    public BigDecimal getDiscountAmount() { return discountAmount; }

    public BigDecimal getCoverCredit() { return coverCredit; }

    public Long getBillForAppointmentId() { return billForAppointmentId; }

    /** A bill payment rather than a booking. */
    public boolean isBill() { return billAmount != null; }

    public Integer getPartySize() { return partySize; }

    public void bookFor(long familyMemberId, String name) {
        this.familyMemberId = familyMemberId;
        this.patientName = name;
    }

    public boolean isActive() {
        return Status.ACTIVE.contains(status);
    }

    /** Payment captured (Phase 8) or not needed: on to approval or straight to confirmed. */
    public void markPaid(boolean needsApproval, Instant now) {
        require(status == Status.PENDING_PAYMENT);
        status = needsApproval ? Status.PENDING_APPROVAL : Status.CONFIRMED;
        updatedAt = now;
    }

    public void confirm(Instant now) {
        require(status == Status.PENDING_APPROVAL);
        status = Status.CONFIRMED;
        updatedAt = now;
    }

    public void decline(String reason, Instant now) {
        require(status == Status.PENDING_APPROVAL);
        status = Status.DECLINED;
        cancelReason = reason;
        cancelledBy = Party.BUSINESS;
        cancelledAt = now;
        refundAmount = totalAmount;
        updatedAt = now;
    }

    public void cancel(Party by, String reason, BigDecimal refund, Instant now) {
        require(isActive());
        status = Status.CANCELLED;
        cancelledBy = by;
        cancelReason = reason;
        cancelledAt = now;
        refundAmount = refund;
        updatedAt = now;
    }

    public void expire(Instant now) {
        require(status == Status.PENDING_PAYMENT);
        status = Status.EXPIRED;
        cancelledBy = Party.SYSTEM;
        cancelledAt = now;
        updatedAt = now;
    }

    public void checkIn(Instant now) {
        require(status == Status.CONFIRMED && checkedInAt == null);
        checkedInAt = now;
        updatedAt = now;
    }

    public void complete(Instant now) {
        require(status == Status.CONFIRMED && !now.isBefore(startsAt));
        status = Status.COMPLETED;
        completedAt = now;
        updatedAt = now;
    }

    public void noShow(Instant now) {
        require(status == Status.CONFIRMED && !now.isBefore(startsAt) && checkedInAt == null);
        status = Status.NO_SHOW;
        updatedAt = now;
    }

    public void reschedule(Long staffId, Instant startsAt, Instant endsAt, Instant blockedUntil, Instant now) {
        require(isActive());
        this.staffId = staffId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.blockedUntil = blockedUntil;
        this.rescheduleCount++;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public Long getCustomerId() { return customerId; }
    public Long getOrganizationId() { return organizationId; }
    public Long getBranchId() { return branchId; }
    public Long getServiceId() { return serviceId; }
    public Long getStaffId() { return staffId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant getBlockedUntil() { return blockedUntil; }
    public Status getStatus() { return status; }
    public String getServiceName() { return serviceName; }
    public BigDecimal getServicePrice() { return servicePrice; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCustomerNote() { return customerNote; }
    public String getCancelReason() { return cancelReason; }
    public Party getCancelledBy() { return cancelledBy; }
    public Instant getCancelledAt() { return cancelledAt; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getRescheduleCount() { return rescheduleCount; }
    public String getPatientName() { return patientName; }
    public Instant getCreatedAt() { return createdAt; }
}
