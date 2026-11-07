package com.bookeasy.payment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One Razorpay order for a booking. */
@Entity
@Table(name = "payments")
public class Payment {

    public enum Status { CREATED, CAPTURED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false, updatable = false)
    private Long appointmentId;

    @Column(name = "razorpay_order_id", nullable = false, updatable = false, length = 64)
    private String orderId;

    @Column(name = "razorpay_payment_id", length = 64)
    private String paymentId;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(nullable = false, columnDefinition = "CHAR(3)")
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.CREATED;

    @Column(length = 32)
    private String method;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(long appointmentId, String orderId, long amountPaise, Instant now) {
        this.appointmentId = appointmentId;
        this.orderId = orderId;
        this.amountPaise = amountPaise;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void captured(String paymentId, String method, Instant now) {
        this.paymentId = paymentId;
        this.method = method;
        this.status = Status.CAPTURED;
        this.capturedAt = now;
        this.updatedAt = now;
    }

    public void failed(String paymentId, Instant now) {
        if (status != Status.CAPTURED) {
            this.paymentId = paymentId;
            this.status = Status.FAILED;
            this.updatedAt = now;
        }
    }

    public Long getId() { return id; }
    public Long getAppointmentId() { return appointmentId; }
    public String getOrderId() { return orderId; }
    public String getPaymentId() { return paymentId; }
    public long getAmountPaise() { return amountPaise; }
    public Status getStatus() { return status; }
    public String getMethod() { return method; }
    public Instant getCapturedAt() { return capturedAt; }
}
