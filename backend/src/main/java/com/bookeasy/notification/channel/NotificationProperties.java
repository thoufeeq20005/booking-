package com.bookeasy.notification.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param devLogDelivery when no provider is configured, write messages (including codes and links) to the
 *                       application log instead of failing. Development only; forced off in prod.
 */
@ConfigurationProperties(prefix = "bookeasy.notifications")
public record NotificationProperties(boolean devLogDelivery, Email email, Sms sms) {

    public record Email(String provider, String smtpHost, int smtpPort, String smtpUsername, String smtpPassword,
                        boolean smtpStarttls, String fromAddress, String fromName) {
    }

    /**
     * @param otpTemplateId DLT-approved OTP template registered with the provider
     * @param otpVariable   name of the template variable that receives the code
     */
    public record Sms(String provider, String apiKey, String baseUrl, String otpTemplateId, String otpVariable) {
    }
}
