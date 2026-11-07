package com.bookeasy.appointment;

/** Published inside the transaction that changes a booking; notifications (Phase 9) listen after commit. */
public record AppointmentChanged(long appointmentId, Kind kind) {

    public enum Kind { BOOKED, CONFIRMED, DECLINED, CANCELLED, RESCHEDULED, COMPLETED, NO_SHOW, EXPIRED, PAID }
}
