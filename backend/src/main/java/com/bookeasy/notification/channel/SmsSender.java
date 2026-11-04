package com.bookeasy.notification.channel;

/**
 * Outbound SMS channel. In India, commercial SMS must use DLT-registered templates, so callers pass a
 * template purpose and variables rather than free text.
 */
public interface SmsSender {

    /** @param phoneE164 e.g. +919876543210 */
    void sendOtp(String phoneE164, String code);
}
