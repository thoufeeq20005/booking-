package com.bookeasy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import com.bookeasy.support.AuthTestBase;

/** Verifying a mobile number on a signed-in account with an SMS code (mobile sign-in itself was removed). */
class PhoneOtpAuthIT extends AuthTestBase {

    private static String randomMobile() {
        return "9" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }

    private org.springframework.test.web.servlet.ResultActions sendCode(Session s, String phone) throws Exception {
        return mvc.perform(authed(postJson("/api/v1/account/phone/send-code", Map.of("phone", phone)), s.accessToken()));
    }

    private org.springframework.test.web.servlet.ResultActions verify(Session s, String phone, String code) throws Exception {
        return mvc.perform(authed(postJson("/api/v1/account/phone/verify", Map.of("phone", phone, "code", code)),
                s.accessToken()));
    }

    @Test
    void mobileSignInEndpointsAreGone() throws Exception {
        mvc.perform(postJson("/api/v1/auth/otp/send", Map.of("phone", randomMobile()))).andExpect(status().isNotFound());
        mvc.perform(postJson("/api/v1/auth/otp/login", Map.of("phone", randomMobile(), "code", "123456")))
                .andExpect(status().isNotFound());
    }

    @Test
    void signedInUserCanVerifyANumberButNotOneOwnedByAnotherAccount() throws Exception {
        Session s = register(uniqueEmail());
        String mobile = randomMobile();
        sendCode(s, "0" + mobile)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.resendAfterSeconds").value(60))
                .andExpect(jsonPath("$.data.demoCode").doesNotExist());
        verify(s, "+91 " + mobile, channels.lastCodeFor("+91" + mobile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("+91" + mobile))
                .andExpect(jsonPath("$.data.phoneVerified").value(true));

        Session other = register(uniqueEmail());
        sendCode(other, mobile)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PHONE_ALREADY_REGISTERED"));
    }

    @Test
    void wrongCodesAreLimitedAndCodesExpire() throws Exception {
        Session s = register(uniqueEmail());
        String mobile = randomMobile();
        sendCode(s, mobile);
        String code = channels.lastCodeFor("+91" + mobile);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            verify(s, mobile, wrong).andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
        }
        // Attempts exhausted: even the right code no longer works.
        verify(s, mobile, code).andExpect(jsonPath("$.error.code").value("OTP_EXPIRED"));

        clock.advance(Duration.ofMinutes(2));
        sendCode(s, mobile);
        String second = channels.lastCodeFor("+91" + mobile);
        clock.advance(Duration.ofMinutes(6));
        verify(s, mobile, second).andExpect(jsonPath("$.error.code").value("OTP_EXPIRED"));
    }

    @Test
    void resendCooldownAndHourlyCapApply() throws Exception {
        Session s = register(uniqueEmail());
        String mobile = randomMobile();
        sendCode(s, mobile).andExpect(status().isOk());
        sendCode(s, mobile)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("OTP_RESEND_TOO_SOON"));
        for (int i = 0; i < 4; i++) {
            clock.advance(Duration.ofSeconds(61));
            sendCode(s, mobile).andExpect(status().isOk());
        }
        clock.advance(Duration.ofSeconds(61));
        sendCode(s, mobile).andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
        assertThat(channels.smsCountFor("+91" + mobile)).isEqualTo(5);
    }

    @Test
    void invalidNumbersAreRejected() throws Exception {
        Session s = register(uniqueEmail());
        for (String bad : new String[]{"12345", "5876543210", "+1 4155552671"}) {
            sendCode(s, bad)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fieldErrors[0].code").value("PHONE_INVALID"));
        }
    }
}
