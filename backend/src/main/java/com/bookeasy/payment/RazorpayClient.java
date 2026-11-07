package com.bookeasy.payment;

import java.util.Map;

/** The few Razorpay REST calls BookEasy makes. Implementations throw {@link RazorpayException} on failure. */
public interface RazorpayClient {

    /** POST /v1/orders with payment_capture=1. Returns the order id. */
    String createOrder(long amountPaise, String receipt, Map<String, String> notes);

    /** POST /v1/payments/{id}/refund. Returns the refund id. */
    String refund(String paymentId, long amountPaise, Map<String, String> notes);

    /** POST /v1/payments/{id}/transfers (Route). Returns the transfer id. */
    String transfer(String paymentId, String linkedAccountId, long amountPaise);

    class RazorpayException extends RuntimeException {
        public RazorpayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
