package com.bookeasy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.bookeasy.support.AuthTestBase;

class PhoneOtpAuthIT extends AuthTestBase {

    private static String randomMobile() {
        return "9" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }

    @Test
    void newNumberSignsUpAsVerifiedCustomerAfterGivingAName() throws Exception {
        String mobile = randomMobile();
        String e164 = "+91" + mobile;
        mvc.perform(postJson("/api/v1/auth/otp/send", Map.of("phone", "0" + mobile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.resendAfterSeconds").value(60))
                .andExpect(jsonPath("$.data.demoCode").doesNotExist());
        String code = channels.lastCodeFor(e164);
        assertThat(code).matches("\\d{6}");

        // Name is required for a new number, and asking for it does not burn the code.
        mvc.perform(postJson("/api/v1/auth/otp/login", Map.of("phone", mobile, "code", code)))
                .andExpect(jsonPath("$.error.code").value("FULL_NAME_REQUIRED"));

        mvc.perform(postJson("/api/v1/auth/otp/login",
                        Map.of("phone", "+91 " + mobile, "code", code, "fullName", "Ravi", "preferredLanguage", "te")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.data.user.phone").value(e164))
                .andExpect(jsonPath("$.data.user.phoneVerified").value(true))
                .andExpect(jsonPath("$.data.user.hasPassword").value(false))
                .andExpect(jsonPath("$.data.user.preferredLanguage").value("te"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("CUSTOMER"));

        // The same code can't be used twice.
        mvc.perform(postJson("/api/v1/auth/otp/login", Map.of("phone", mobile, "code", code)))
                .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
    }

    @Test
    void existingNumberSignsInWithoutAName() throws Exception {
        String mobile = randomMobile();
        mvc.perform(postJson("/api/v1/auth/otp/send", Map.of("phone", mobile)));
        mvc.perform(postJson("/api/v1/auth/otp/login",
                Map.of("phone", mobile, "code", channels.lastCodeFor("+91" + mobile), "fullName", "Meena")));

        clock.advance(Duration.ofMinutes(2));
        mvc.perform(postJson("/api/v1/auth/otp/send", Map.of("phone", mobile)));
        MvcResult r = mvc.perform(postJson("/api/v1/auth/otp/login",
                        Map.of("phone", mobile, "code", channels.lastCodeFor("+91" + mobile))))
                .andExpect(status().isOk()).andReturn();
        assertThat(read(r).path("data").path("user").path("fullName").asText()).isEqualTo("Meena");
    }

    @Test
    void wrongCodesAreLimitedAndCodesExpire() throws Exception {
        String mobile = randomMobile();
        mvc.perform(postJson("/api/v1/auth/otp/send", Map.of("phone", mobile)));
        String code = channels.lastCodeFor("+91" + mobile);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            mvc.perform(postJson("/api/v1/auth/otp/login", Map.of("phone", mobile, "code", wrong, "fullName", "A")))
                    .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
        }
        // Attempts exhausted: even the right code no longer works.
        mvc.perform(postJson("/api/v1/auth/otp/login", Map.of("phone", mobile, "code", code, "fullName", "A")))
                .andExpect(jsonPath("$.error.code").value("OTP_EXPIRED"));

        clock.advance(Duration.ofMinutes(2));
        mvc.perform(postJson("/api/v1/auth/otp/send", Map.of("phone", mobile)));
        String second = channels.lastCodeFor("+91" + mobile);
        clock.advance(Duration.ofMinutes(6));
        mvc.perform(postJson("/api/v1/auth/otp/login", Map.of("phone", mobile, "code", second, "fullName", "A")))
                .andExpect(jsonPath("$.error.code").value("OTP_EXPIRED"));
    }

    @Test
    void resendCooldownAndHourlyCapApply() throws Exception {
        String mobile = randomMobile();
        mvc.perform(postJson("/api/v1/auth/otp/send", Map.of("phone", mobile))).andExpect(status().isOk());
        mvc.perform(postJson("/api/v1/auth/otp/send", Map.of("phone", mobile)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("OTP_RESEND_TOO_SOON"));
        for (int i = 0; i < 4; i++) {
            clock.advance(Duration.ofSeconds(61));
            mvc.perform(postJson("/api/v1/auth/otp/send", Map.of("phone", mobile))).andExpect(status().isOk());
        }
        clock.advance(Duration.ofSeconds(61));
        mvc.perform(postJson("/api/v1/auth/otp/send", Map.of("phone", mobile)))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
        assertThat(channels.smsCountFor("+91" + mobile)).isEqualTo(5);
    }

    @Test
    void invalidNumbersAreRejected() throws Exception {
        for (String bad : new String[]{"12345", "5876543210", "+1 4155552671"}) {
            mvc.perform(postJson("/api/v1/auth/otp/send", Map.of("phone", bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fieldErrors[0].code").value("PHONE_INVALID"));
        }
    }

    @Test
    void signedInUserCanVerifyANumberButNotOneOwnedByAnotherAccount() throws Exception {
        Session s = register(uniqueEmail());
        String mobile = randomMobile();
        mvc.perform(authed(postJson("/api/v1/account/phone/send-code", Map.of("phone", mobile)), s.accessToken()))
                .andExpect(status().isOk());
        mvc.perform(authed(postJson("/api/v1/account/phone/verify",
                        Map.of("phone", mobile, "code", channels.lastCodeFor("+91" + mobile))), s.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("+91" + mobile))
                .andExpect(jsonPath("$.data.phoneVerified").value(true));

        Session other = register(uniqueEmail());
        mvc.perform(authed(postJson("/api/v1/account/phone/send-code", Map.of("phone", mobile)), other.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PHONE_ALREADY_REGISTERED"));
    }
}
