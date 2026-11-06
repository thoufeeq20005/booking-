package com.bookeasy.notification.channel;

/**
 * Outbound SMS channel. In India, commercial SMS must use DLT-registered templates, so callers pass a
 * template purpose and variables rather than free text.
 */
public interface SmsSender {

    /** @param phoneE164 e.g. +919876543210 */
    void sendOtp(String phoneE164, String code);

    /**
     * False only for the development stand-in that writes codes to the log. The API then returns the code so the
     * demo UI can show it on screen; with a real provider (or in production) codes never leave the server.
     */
    default boolean deliversToPhone() {
        return true;
    }
}
