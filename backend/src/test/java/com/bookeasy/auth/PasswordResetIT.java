package com.bookeasy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bookeasy.support.AuthTestBase;

class PasswordResetIT extends AuthTestBase {

    private static final String NEW_PASSWORD = "Monsoon2027";

    @Test
    void unknownEmailGetsTheSameAcceptedResponseAndNoEmail() throws Exception {
        String email = uniqueEmail();
        mvc.perform(postJson("/api/v1/auth/password/forgot", Map.of("email", email)))
                .andExpect(status().isAccepted());
        assertThat(channels.emailsTo(email)).isEmpty();
    }

    @Test
    void resetSetsNewPasswordSignsOutEverywhereAndIsSingleUse() throws Exception {
        String email = uniqueEmail();
        Session session = register(email);
        mvc.perform(postJson("/api/v1/auth/password/forgot", Map.of("email", email))).andExpect(status().isAccepted());
        String token = channels.lastTokenFor(email);

        mvc.perform(postJson("/api/v1/auth/password/reset", Map.of("token", token, "newPassword", NEW_PASSWORD)))
                .andExpect(status().isOk());

        mvc.perform(me(session.accessToken())).andExpect(status().isUnauthorized());
        mvc.perform(refreshRequest(session.refreshToken())).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD)))
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        Session fresh = login(email, NEW_PASSWORD);
        mvc.perform(me(fresh.accessToken())).andExpect(jsonPath("$.data.emailVerified").value(true));

        mvc.perform(postJson("/api/v1/auth/password/reset", Map.of("token", token, "newPassword", "Another2028")))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void expiredResetLinkIsRejectedAndPolicyApplies() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(postJson("/api/v1/auth/password/forgot", Map.of("email", email)));
        String token = channels.lastTokenFor(email);

        mvc.perform(postJson("/api/v1/auth/password/reset", Map.of("token", token, "newPassword", "short")))
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("PASSWORD_TOO_SHORT"));

        clock.advance(Duration.ofMinutes(31));
        mvc.perform(postJson("/api/v1/auth/password/reset", Map.of("token", token, "newPassword", NEW_PASSWORD)))
                .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void forgotPasswordIsRateLimitedPerEmail() throws Exception {
        String email = uniqueEmail();
        register(email);
        for (int i = 0; i < 3; i++) {
            mvc.perform(postJson("/api/v1/auth/password/forgot", Map.of("email", email))).andExpect(status().isAccepted());
        }
        mvc.perform(postJson("/api/v1/auth/password/forgot", Map.of("email", email)))
                .andExpect(status().isTooManyRequests());
        assertThat(channels.emailsTo(email).stream().filter(e -> e.subject().contains("Reset"))).hasSize(3);
    }

    @Test
    void signedInPasswordChangeNeedsCurrentPasswordAndSignsOutOtherSessions() throws Exception {
        String email = uniqueEmail();
        Session a = register(email);
        Session b = login(email, PASSWORD);

        mvc.perform(authed(postJson("/api/v1/account/password",
                        Map.of("currentPassword", "Wrong12345", "newPassword", NEW_PASSWORD)), a.accessToken()))
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("PASSWORD_INCORRECT"));

        mvc.perform(authed(postJson("/api/v1/account/password",
                        Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD)), a.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        mvc.perform(me(b.accessToken())).andExpect(status().isUnauthorized());
        mvc.perform(refreshRequest(b.refreshToken())).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD);
    }

    @Test
    void resendVerificationIsLimited() throws Exception {
        Session s = register(uniqueEmail());
        for (int i = 0; i < 3; i++) {
            mvc.perform(authed(post("/api/v1/account/email/resend-verification"), s.accessToken()))
                    .andExpect(status().isOk());
        }
        mvc.perform(authed(post("/api/v1/account/email/resend-verification"), s.accessToken()))
                .andExpect(status().isTooManyRequests());
    }
}
