package com.bookeasy.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay settings. Online payment switches on only when key id, key secret and webhook secret are all set.
 * Without them, {@code demoMode} (development only; always off in prod and tests) runs a simulated gateway so the
 * payment page can be tried without a Razorpay account. Otherwise customers pay at the venue.
 */
@ConfigurationProperties(prefix = "bookeasy.payments")
public record PaymentProperties(Razorpay razorpay, Integer holdMinutes, Boolean demoMode) {

    public record Razorpay(String keyId, String keySecret, String webhookSecret, boolean routeEnabled, String baseUrl) {
    }

    public boolean enabled() {
        return razorpay != null && notBlank(razorpay.keyId()) && notBlank(razorpay.keySecret())
                && notBlank(razorpay.webhookSecret());
    }

    /** Simulated payments: only when Razorpay is not configured and demo mode is switched on. */
    public boolean demo() {
        return !enabled() && Boolean.TRUE.equals(demoMode);
    }

    /** Bookings go through the payment page (real Razorpay or the demo gateway). */
    public boolean online() {
        return enabled() || demo();
    }

    public int hold() {
        return holdMinutes == null ? 15 : holdMinutes;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
