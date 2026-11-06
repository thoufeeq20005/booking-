package com.bookeasy.notification.channel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Only the development log stand-in may let the API reveal an OTP (as the on-screen demo code). */
class SmsSenderSelectionTest {

    private static NotificationProperties props(boolean devLog, String provider) {
        return new NotificationProperties(devLog,
                new NotificationProperties.Email("", null, 587, null, null, true, "no-reply@example.test", "BookEasy"),
                new NotificationProperties.Sms(provider, "key", "https://control.msg91.com", "tpl", "otp"));
    }

    private final NotificationChannelConfig config = new NotificationChannelConfig();

    @Test
    void devWithoutProviderShowsDemoCodes() {
        assertThat(config.smsSender(props(true, ""), RestClient.builder()).deliversToPhone()).isFalse();
    }

    @Test
    void productionWithoutProviderNeverShowsCodes() {
        assertThat(config.smsSender(props(false, ""), RestClient.builder()).deliversToPhone()).isTrue();
    }

    @Test
    void emailCodesAreOnlyVisibleWithTheDevLogger() {
        assertThat(config.emailSender(props(true, "")).deliversToInbox()).isFalse();
        assertThat(config.emailSender(props(false, "")).deliversToInbox()).isTrue();
    }

    @Test
    void realProviderNeverShowsCodes() {
        assertThat(config.smsSender(props(true, "msg91"), RestClient.builder()).deliversToPhone()).isTrue();
    }
}
