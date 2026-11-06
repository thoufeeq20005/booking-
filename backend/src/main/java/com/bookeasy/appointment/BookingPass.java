package com.bookeasy.appointment;

import java.time.Instant;

/**
 * What the public pass page shows to anyone holding a valid (signed) booking QR. Deliberately limited: no phone,
 * email, price or notes, and only the customer's first name.
 */
public record BookingPass(
        String reference,
        Appointment.Status status,
        Instant startsAt,
        Instant endsAt,
        String timezone,
        String organizationName,
        String organizationSlug,
        String branchName,
        String branchAddress,
        String serviceName,
        String staffName,
        String guestFirstName,
        boolean paid,
        Instant checkedInAt) {

    static BookingPass of(AppointmentView v) {
        String who = v.patientName() != null ? v.patientName() : v.customerName();
        String first = who == null ? null : who.trim().split("\\s+")[0];
        return new BookingPass(v.reference(), v.status(), v.startsAt(), v.endsAt(), v.timezone(), v.organizationName(),
                v.organizationSlug(), v.branchName(), v.branchAddress(), v.serviceName(), v.staffName(), first, v.paid(),
                v.checkedInAt());
    }
}
