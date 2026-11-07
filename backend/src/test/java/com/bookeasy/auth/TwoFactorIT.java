package com.bookeasy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.bookeasy.auth.twofactor.Totp;
import com.bookeasy.support.AuthTestBase;
import com.bookeasy.support.Base32Decoder;

class TwoFactorIT extends AuthTestBase {

    private String code(byte[] secret) {
        return Totp.codeAt(secret, Totp.stepAt(clock.instant()), Totp.DIGITS);
    }

    private byte[] enrol(Session s) throws Exception {
        MvcResult setup = mvc.perform(authed(post("/api/v1/account/two-factor/setup"), s.accessToken()))
                .andExpect(status().isOk()).andReturn();
        String secretB32 = read(setup).path("data").path("secret").asText();
        assertThat(read(setup).path("data").path("otpauthUri").asText())
                .startsWith("otpauth://totp/BookEasy:").contains("secret=" + secretB32, "issuer=BookEasy");
        byte[] secret = Base32Decoder.decode(secretB32);
        mvc.perform(authed(postJson("/api/v1/account/two-factor/enable", Map.of("code", code(secret))), s.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.twoFactorEnabled").value(true));
        return secret;
    }

    @Test
    void enabledTwoFactorTurnsLoginIntoAChallenge() throws Exception {
        String email = uniqueEmail();
        byte[] secret = enrol(register(email));
        clock.advance(Duration.ofSeconds(31));

        Session challenged = login(email, PASSWORD);
        assertThat(challenged.body().path("status").asText()).isEqualTo("TWO_FACTOR_REQUIRED");
        assertThat(challenged.accessToken()).isNull();
        assertThat(challenged.refreshToken()).isNull();
        String challenge = challenged.body().path("challengeToken").asText();

        // The challenge token is not an access token.
        mvc.perform(me(challenge)).andExpect(status().isUnauthorized());

        String wrong = code(secret).equals("000000") ? "111111" : "000000";
        mvc.perform(postJson("/api/v1/auth/login/two-factor", Map.of("challengeToken", challenge, "code", wrong)))
                .andExpect(jsonPath("$.error.code").value("TWO_FACTOR_INVALID"));

        String good = code(secret);
        MvcResult ok = mvc.perform(postJson("/api/v1/auth/login/two-factor",
                        Map.of("challengeToken", challenge, "code", good)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AUTHENTICATED"))
                .andReturn();
        assertThat(cookieValue(ok)).isNotBlank();

        // Replaying the same code is refused.
        mvc.perform(postJson("/api/v1/auth/login/two-factor", Map.of("challengeToken", challenge, "code", good)))
                .andExpect(jsonPath("$.error.code").value("TWO_FACTOR_INVALID"));
    }

    @Test
    void challengeExpiresAndAttemptsAreLimited() throws Exception {
        String email = uniqueEmail();
        enrol(register(email));
        clock.advance(Duration.ofSeconds(31));
        String challenge = login(email, PASSWORD).body().path("challengeToken").asText();
        for (int i = 0; i < 5; i++) {
            mvc.perform(postJson("/api/v1/auth/login/two-factor", Map.of("challengeToken", challenge, "code", "123456")));
        }
        mvc.perform(postJson("/api/v1/auth/login/two-factor", Map.of("challengeToken", challenge, "code", "123456")))
                .andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofMinutes(6));
        mvc.perform(postJson("/api/v1/auth/login/two-factor", Map.of("challengeToken", challenge, "code", "123456")))
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"));
    }

    @Test
    void disablingNeedsPasswordAndCurrentCode() throws Exception {
        String email = uniqueEmail();
        Session s = register(email);
        byte[] secret = enrol(s);
        clock.advance(Duration.ofSeconds(31));

        mvc.perform(authed(postJson("/api/v1/account/two-factor/disable",
                        Map.of("code", code(secret), "password", "Wrong12345")), s.accessToken()))
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("PASSWORD_INCORRECT"));
        mvc.perform(authed(postJson("/api/v1/account/two-factor/disable",
                        Map.of("code", code(secret), "password", PASSWORD)), s.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.twoFactorEnabled").value(false));

        assertThat(login(email, PASSWORD).body().path("status").asText()).isEqualTo("AUTHENTICATED");
    }

    @Test
    void enableRequiresSetupFirst() throws Exception {
        Session s = register(uniqueEmail());
        mvc.perform(authed(postJson("/api/v1/account/two-factor/enable", Map.of("code", "123456")), s.accessToken()))
                .andExpect(jsonPath("$.error.code").value("TWO_FACTOR_SETUP_REQUIRED"));
    }
}
