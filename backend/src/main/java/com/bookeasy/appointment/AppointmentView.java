package com.bookeasy.appointment;

import java.math.BigDecimal;
import java.time.Instant;

/** What customers, owners and staff see of a booking. {@code qrPayload} is only filled for the customer. */
public record AppointmentView(
        long id,
        String reference,
        Appointment.Status status,
        Instant startsAt,
        Instant endsAt,
        String timezone,
        long organizationId,
        String organizationName,
        String organizationSlug,
        long branchId,
        String branchName,
        String branchAddress,
        long serviceId,
        String serviceName,
        Long staffId,
        String staffName,
        String customerName,
        String customerPhone,
        BigDecimal servicePrice,
        BigDecimal platformFee,
        BigDecimal totalAmount,
        String customerNote,
        String cancelReason,
        Appointment.Party cancelledBy,
        BigDecimal refundAmount,
        Instant checkedInAt,
        int rescheduleCount,
        String qrPayload,
        Instant createdAt) {
}
