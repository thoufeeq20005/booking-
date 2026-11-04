package com.bookeasy.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay settings. Online payment switches on only when key id, key secret and webhook secret are all set;
 * otherwise customers pay at the venue.
 */
@ConfigurationProperties(prefix = "bookeasy.payments")
public record PaymentProperties(Razorpay razorpay, Integer holdMinutes) {

    public record Razorpay(String keyId, String keySecret, String webhookSecret, boolean routeEnabled, String baseUrl) {
    }

    public boolean enabled() {
        return razorpay != null && notBlank(razorpay.keyId()) && notBlank(razorpay.keySecret())
                && notBlank(razorpay.webhookSecret());
    }

    public int hold() {
        return holdMinutes == null ? 15 : holdMinutes;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
