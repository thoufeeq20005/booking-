package com.bookeasy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.bookeasy.auth.twofactor.Totp;
import com.bookeasy.notification.channel.EmailSender.EmailMessage;
import com.bookeasy.support.AuthTestBase;
import com.bookeasy.support.Base32Decoder;
import com.fasterxml.jackson.databind.JsonNode;

/** Password sign-in, then a 6-digit code emailed to the account, then the session. */
@TestPropertySource(properties = "bookeasy.auth.password-login-email-code=true")
class PasswordEmailCodeIT extends AuthTestBase {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private String lastEmailedCode(String email) {
        List<EmailMessage> mine = channels.emailsTo(email);
        assertThat(mine).as("emails to " + email).isNotEmpty();
        Matcher m = CODE.matcher(mine.get(mine.size() - 1).textBody());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private JsonNode passwordStep(String email) throws Exception {
        return read(mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EMAIL_CODE_REQUIRED"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(cookie().doesNotExist("be_rt")) // not signed in yet
                .andReturn()).path("data");
    }

    @Test
    void theRightPasswordEmailsACodeAndTheCodeSignsIn() throws Exception {
        String email = uniqueEmail();
        register(email);
        channels.clear();

        JsonNode challenge = passwordStep(email);
        assertThat(challenge.path("emailCode").path("expiresInSeconds").asLong()).isEqualTo(300);
        assertThat(challenge.path("emailCode").path("resendAfterSeconds").asLong()).isEqualTo(60);
        String token = challenge.path("challengeToken").asText();
        String code = lastEmailedCode(email);

        String wrong = code.equals("000000") ? "111111" : "000000";
        mvc.perform(postJson("/api/v1/auth/login/email-code", Map.of("challengeToken", token, "code", wrong)))
                .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
        mvc.perform(postJson("/api/v1/auth/login/email-code", Map.of("challengeToken", token, "code", code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(cookie().exists("be_rt"));
        // Single use.
        mvc.perform(postJson("/api/v1/auth/login/email-code", Map.of("challengeToken", token, "code", code)))
                .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
    }

    @Test
    void wrongPasswordSendsNothing() throws Exception {
        String email = uniqueEmail();
        register(email);
        channels.clear();
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", "Wrong-password-1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        assertThat(channels.emailsTo(email)).isEmpty();
    }

    @Test
    void resendWaitsAMinuteAndCodesExpireAfterFive() throws Exception {
        String email = uniqueEmail();
        register(email);
        channels.clear();
        String token = passwordStep(email).path("challengeToken").asText();
        String first = lastEmailedCode(email);

        mvc.perform(postJson("/api/v1/auth/login/email-code/resend", Map.of("challengeToken", token)))
                .andExpect(jsonPath("$.error.code").value("OTP_RESEND_TOO_SOON"));
        // Signing in again within the minute keeps the code already sent instead of failing.
        assertThat(passwordStep(email).path("emailCode").path("resendAfterSeconds").asLong()).isEqualTo(60);
        assertThat(channels.emailsTo(email)).hasSize(1);

        clock.advance(Duration.ofSeconds(61));
        mvc.perform(postJson("/api/v1/auth/login/email-code/resend", Map.of("challengeToken", token)))
                .andExpect(status().isOk());
        assertThat(channels.emailsTo(email)).hasSize(2);
        String second = lastEmailedCode(email);

        clock.advance(Duration.ofMinutes(6)); // the challenge (5 min) and the code (5 min) have both expired
        mvc.perform(postJson("/api/v1/auth/login/email-code", Map.of("challengeToken", token, "code", second)))
                .andExpect(status().is4xxClientError());
        assertThat(first).isNotNull();
    }

    @Test
    void authenticatorAppUsersGetThatStepInstead() throws Exception {
        String email = uniqueEmail();
        Session s = register(email);
        String secretB32 = read(mvc.perform(authed(post("/api/v1/account/two-factor/setup"), s.accessToken())).andReturn())
                .path("data").path("secret").asText();
        byte[] secret = Base32Decoder.decode(secretB32);
        mvc.perform(authed(postJson("/api/v1/account/two-factor/enable",
                Map.of("code", Totp.codeAt(secret, Totp.stepAt(clock.instant()), Totp.DIGITS))), s.accessToken()))
                .andExpect(status().isOk());
        channels.clear();

        String token = read(mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD)))
                .andExpect(jsonPath("$.data.status").value("TWO_FACTOR_REQUIRED")).andReturn())
                .path("data").path("challengeToken").asText();
        assertThat(channels.emailsTo(email)).isEmpty();
        // That challenge can't be finished, or skipped, with an email code.
        mvc.perform(postJson("/api/v1/auth/login/email-code/resend", Map.of("challengeToken", token)))
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"));
        mvc.perform(postJson("/api/v1/auth/login/email-code", Map.of("challengeToken", token, "code", "123456")))
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"));
    }
}
