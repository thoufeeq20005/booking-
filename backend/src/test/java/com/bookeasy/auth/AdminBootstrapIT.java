package com.bookeasy.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bookeasy.support.AuthTestBase;

class AdminBootstrapIT extends AuthTestBase {

    // Must match bookeasy.auth.bootstrap.* in application-test.yml.
    private static final String EMAIL = "founder@bookeasy.test";
    private static final String TOKEN = "test-bootstrap-token-0123456789abcdef";

    @BeforeEach
    void removeExistingSuperAdmins() {
        jdbc.update("DELETE u FROM users u JOIN user_roles r ON r.user_id = u.id WHERE r.role_id = 4");
        jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
    }

    private Map<String, String> request(String token, String email) {
        return Map.of("token", token, "fullName", "Founder", "email", email, "password", "Bootstrap2026");
    }

    @Test
    void createsTheFirstSuperAdminOnceWithTheConfiguredTokenAndEmail() throws Exception {
        mvc.perform(postJson("/api/v1/public/bootstrap/super-admin", request("wrong-token-0123456789abcdef0123", EMAIL)))
                .andExpect(status().isForbidden());
        mvc.perform(postJson("/api/v1/public/bootstrap/super-admin", request(TOKEN, "someone@else.test")))
                .andExpect(status().isForbidden());

        mvc.perform(postJson("/api/v1/public/bootstrap/super-admin", request(TOKEN, EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.data.emailVerified").value(true));

        String token = login(EMAIL, "Bootstrap2026").accessToken();
        mvc.perform(authed(get("/api/v1/admin/users"), token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());

        Integer audits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'SUPER_ADMIN_BOOTSTRAPPED'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(audits).isGreaterThanOrEqualTo(1);

        // Once a Super Admin exists the endpoint is dead, even with the right token.
        mvc.perform(postJson("/api/v1/public/bootstrap/super-admin", request(TOKEN, EMAIL)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BOOTSTRAP_ALREADY_DONE"));
    }
}
