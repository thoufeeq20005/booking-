package com.bookeasy.appointment;

/**
 * Decides whether a new booking waits in PENDING_PAYMENT for an online payment. Until payments exist every
 * booking skips straight to approval/confirmation; the payments module provides the real implementation.
 */
public interface PaymentGate {

    boolean requiresOnlinePayment(Appointment appointment);
}
