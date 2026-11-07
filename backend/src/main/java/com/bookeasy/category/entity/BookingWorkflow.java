package com.bookeasy.category.entity;

/** Which booking wizard a category uses (Phase 7): what to ask the customer and how to label staff. */
public enum BookingWorkflow {
    HEALTHCARE,
    SALON,
    EDUCATION,
    GENERIC,
    /** Movie tickets: showtimes and seat selection. */
    CINEMA,
    /** Restaurants and cafés: table bookings for a party size. */
    DINING
}
