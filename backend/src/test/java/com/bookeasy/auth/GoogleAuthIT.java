package com.bookeasy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import com.bookeasy.auth.google.TestGoogleTokens;
import com.bookeasy.support.AuthTestBase;

class GoogleAuthIT extends AuthTestBase {

    @Autowired
    TestGoogleTokens google;

    private MvcResult signIn(String idToken) throws Exception {
        return mvc.perform(postJson("/api/v1/auth/google", Map.of("idToken", idToken))).andReturn();
    }

    @Test
    void firstSignInCreatesAVerifiedCustomerAndLaterSignInsReuseIt() throws Exception {
        String sub = UUID.randomUUID().toString();
        String email = uniqueEmail();
        MvcResult first = signIn(google.token(sub, email, true, "Priya Nair"));
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        var user = read(first).path("data").path("user");
        assertThat(user.path("email").asText()).isEqualTo(email);
        assertThat(user.path("emailVerified").asBoolean()).isTrue();
        assertThat(user.path("fullName").asText()).isEqualTo("Priya Nair");
        assertThat(cookieValue(first)).isNotBlank();

        MvcResult second = signIn(google.token(sub, email, true, "Priya Nair"));
        assertThat(read(second).path("data").path("user").path("id").asLong()).isEqualTo(user.path("id").asLong());
    }

    @Test
    void linksToAnExistingAccountWithTheSameVerifiedEmail() throws Exception {
        String email = uniqueEmail();
        Session existing = register(email);
        long existingId = existing.body().path("user").path("id").asLong();

        MvcResult r = signIn(google.token(UUID.randomUUID().toString(), email, true, "Asha"));
        assertThat(read(r).path("data").path("user").path("id").asLong()).isEqualTo(existingId);
        assertThat(read(r).path("data").path("user").path("emailVerified").asBoolean()).isTrue();
    }

    @Test
    void unverifiedGoogleEmailCannotCreateOrClaimAnAccount() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(postJson("/api/v1/auth/google",
                        Map.of("idToken", google.token(UUID.randomUUID().toString(), email, false, "Mallory"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("GOOGLE_TOKEN_INVALID"));
    }

    @Test
    void tokensForAnotherAudienceOrIssuerOrForgedAreRejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String email = uniqueEmail();
        for (String token : new String[]{
                google.token(sub, email, true, "A", "someone-else.apps.googleusercontent.com", "https://accounts.google.com"),
                google.token(sub, email, true, "A", TestGoogleTokens.CLIENT_ID, "https://evil.example"),
                new TestGoogleTokens(clock).token(sub, email, true, "A"),
                "garbage"}) {
            mvc.perform(postJson("/api/v1/auth/google", Map.of("idToken", token)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("GOOGLE_TOKEN_INVALID"));
        }
    }
}
