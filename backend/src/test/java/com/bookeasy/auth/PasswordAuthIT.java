package com.bookeasy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import com.bookeasy.auth.web.AuthWebSupport;
import com.bookeasy.support.AuthTestBase;

class PasswordAuthIT extends AuthTestBase {

    @Test
    void registrationSignsInWithHttpOnlyRefreshCookieAndSendsVerificationEmail() throws Exception {
        String email = uniqueEmail();
        MvcResult r = mvc.perform(postJson("/api/v1/auth/register",
                        Map.of("fullName", "Asha", "email", email.toUpperCase(), "password", PASSWORD,
                                "preferredLanguage", "ta")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.data.session.user.email").value(email))
                .andExpect(jsonPath("$.data.session.user.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.session.user.emailVerified").value(false))
                .andExpect(jsonPath("$.data.verificationEmailSent").value(true))
                .andExpect(jsonPath("$.data.session.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = r.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("be_rt=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
        // Email is rendered in the preferred language.
        assertThat(channels.emailsTo(email).get(0).subject()).contains("BookEasy").doesNotContain("Verify");

        String token = channels.lastTokenFor(email);
        mvc.perform(postJson("/api/v1/auth/email/verify", Map.of("token", token))).andExpect(status().isOk());
        String access = read(r).path("data").path("session").path("accessToken").asText();
        mvc.perform(me(access)).andExpect(jsonPath("$.data.emailVerified").value(true));
        // Single use.
        mvc.perform(postJson("/api/v1/auth/email/verify", Map.of("token", token)))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void expiredVerificationLinkIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email);
        clock.advance(Duration.ofHours(25));
        mvc.perform(postJson("/api/v1/auth/email/verify", Map.of("token", channels.lastTokenFor(email))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void duplicateEmailAndWeakPasswordsAreRejected() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(postJson("/api/v1/auth/register", Map.of("fullName", "X", "email", email, "password", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));
        mvc.perform(postJson("/api/v1/auth/register",
                        Map.of("fullName", "X", "email", uniqueEmail(), "password", "short1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.error.fieldErrors[0].code").value("PASSWORD_TOO_SHORT"));
        mvc.perform(postJson("/api/v1/auth/register", Map.of("fullName", "", "email", "not-an-email", "password", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", "Wrong12345")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", uniqueEmail(), "password", "Wrong12345")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void accountLocksAfterFiveFailuresAndUnlocksAfterTheLockoutPeriod() throws Exception {
        String email = uniqueEmail();
        register(email);
        for (int i = 0; i < 4; i++) {
            mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", "Wrong12345")))
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        }
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", "Wrong12345")))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));
        // Even the right password is refused while locked.
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD)))
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));

        clock.advance(Duration.ofMinutes(16));
        jdbc.update("DELETE FROM rate_limit_counters");
        login(email, PASSWORD);
    }

    @Test
    void loginIsRateLimitedPerEmail() throws Exception {
        String email = uniqueEmail();
        for (int i = 0; i < 10; i++) {
            mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", "Wrong12345")));
        }
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", "Wrong12345")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void refreshRotatesTheCookieAndDetectsReuse() throws Exception {
        Session s = register(uniqueEmail());

        // Without the custom header the cookie alone is not enough (CSRF defence).
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("be_rt", s.refreshToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_HEADER_MISSING"));

        MvcResult rotated = mvc.perform(refreshRequest(s.refreshToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String next = cookieValue(rotated);
        assertThat(next).isNotBlank().isNotEqualTo(s.refreshToken());

        // A second tab racing with the old cookie moments later is refused but does not end the session.
        mvc.perform(refreshRequest(s.refreshToken())).andExpect(status().isUnauthorized());
        MvcResult again = mvc.perform(refreshRequest(next)).andExpect(status().isOk()).andReturn();
        next = cookieValue(again);

        // Replaying an old token later is treated as theft: the whole family, including the newest token, dies.
        clock.advance(Duration.ofSeconds(11));
        mvc.perform(refreshRequest(s.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        mvc.perform(refreshRequest(next)).andExpect(status().isUnauthorized());
    }

    @Test
    void staleAccessTokenDoesNotBlockRefresh() throws Exception {
        Session s = register(uniqueEmail());
        clock.advance(Duration.ofMinutes(16));
        mvc.perform(me(s.accessToken())).andExpect(status().isUnauthorized());
        mvc.perform(authed(refreshRequest(s.refreshToken()), s.accessToken())).andExpect(status().isOk());
    }

    @Test
    void refreshTokenExpiresAfterItsLifetime() throws Exception {
        Session s = register(uniqueEmail());
        clock.advance(Duration.ofDays(15));
        mvc.perform(refreshRequest(s.refreshToken())).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesThisDeviceAndLogoutAllRevokesAccessTokensImmediately() throws Exception {
        String email = uniqueEmail();
        Session a = register(email);
        Session b = login(email, PASSWORD);

        mvc.perform(post("/api/v1/auth/logout").header(AuthWebSupport.CLIENT_HEADER, "web")
                        .cookie(new Cookie("be_rt", a.refreshToken())))
                .andExpect(status().isNoContent());
        mvc.perform(refreshRequest(a.refreshToken())).andExpect(status().isUnauthorized());
        mvc.perform(me(b.accessToken())).andExpect(status().isOk());

        mvc.perform(authed(post("/api/v1/account/logout-all"), b.accessToken())).andExpect(status().isNoContent());
        mvc.perform(me(b.accessToken())).andExpect(status().isUnauthorized());
        mvc.perform(refreshRequest(b.refreshToken())).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedOrForeignTokensAreRejected() throws Exception {
        Session s = register(uniqueEmail());
        String tampered = s.accessToken().substring(0, s.accessToken().length() - 3) + "abc";
        mvc.perform(me(tampered)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(me("not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void profileUpdateChangesNameAndLanguage() throws Exception {
        Session s = register(uniqueEmail());
        mvc.perform(authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/account/me"),
                        s.accessToken()).contentType("application/json")
                        .content(body(Map.of("fullName", "Asha K", "preferredLanguage", "kn"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Asha K"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("kn"));
        mvc.perform(authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/account/me"),
                        s.accessToken()).contentType("application/json")
                        .content(body(Map.of("preferredLanguage", "fr"))))
                .andExpect(status().isBadRequest());
    }
}
