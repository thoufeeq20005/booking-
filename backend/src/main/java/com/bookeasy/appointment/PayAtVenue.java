package com.bookeasy.appointment;

import org.springframework.stereotype.Component;

/** Until online payments exist, customers pay at the venue and bookings skip PENDING_PAYMENT. */
@Component
public class PayAtVenue implements PaymentGate {

    @Override
    public boolean requiresOnlinePayment(Appointment appointment) {
        return false;
    }
}
