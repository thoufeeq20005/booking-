package com.bookeasy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import com.bookeasy.support.AuthTestBase;
import com.bookeasy.user.entity.RoleCode;
import com.bookeasy.user.entity.User;

class RoleAccessIT extends AuthTestBase {

    private static final Map<RoleCode, String> PREFIX = Map.of(
            RoleCode.CUSTOMER, "/api/v1/customer/_probe",
            RoleCode.BUSINESS_OWNER, "/api/v1/owner/_probe",
            RoleCode.STAFF, "/api/v1/staff/_probe",
            RoleCode.SUPER_ADMIN, "/api/v1/admin/_probe");

    @ParameterizedTest
    @EnumSource(RoleCode.class)
    void eachRoleReachesOnlyItsOwnArea(RoleCode role) throws Exception {
        String email = uniqueEmail();
        createUser(email, role);
        String token = login(email, PASSWORD).accessToken();

        for (var entry : PREFIX.entrySet()) {
            int expected = entry.getKey() == role ? 200 : 403;
            int actual = mvc.perform(authed(get(entry.getValue()), token)).andReturn().getResponse().getStatus();
            assertThat(actual).as("%s calling %s", role, entry.getValue()).isEqualTo(expected);
        }
    }

    @Test
    void anonymousCallersGet401EverywhereProtected() throws Exception {
        for (String path : List.copyOf(PREFIX.values())) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/v1/account/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotUseAdminUserManagement() throws Exception {
        Session customer = register(uniqueEmail());
        mvc.perform(authed(get("/api/v1/admin/users"), customer.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminCanSearchAndSuspendUsersWhichTakesEffectImmediatelyAndIsAudited() throws Exception {
        String adminEmail = uniqueEmail();
        User admin = createUser(adminEmail, RoleCode.SUPER_ADMIN);
        String adminToken = login(adminEmail, PASSWORD).accessToken();

        String victimEmail = uniqueEmail();
        Session victim = register(victimEmail);
        long victimId = victim.body().path("user").path("id").asLong();

        mvc.perform(authed(get("/api/v1/admin/users").param("q", victimEmail.substring(0, 10)), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].email").value(victimEmail))
                .andExpect(jsonPath("$.data.totalItems").value(1));
        mvc.perform(authed(get("/api/v1/admin/users").param("role", "SUPER_ADMIN").param("size", "100"), adminToken))
                .andExpect(jsonPath("$.data.items[?(@.email == '" + adminEmail + "')]").exists())
                .andExpect(jsonPath("$.data.items[?(@.email == '" + victimEmail + "')]").doesNotExist());

        mvc.perform(authed(patch("/api/v1/admin/users/" + victimId + "/status"), adminToken)
                        .contentType("application/json").content(body(Map.of("status", "SUSPENDED", "reason", "fraud"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        mvc.perform(me(victim.accessToken())).andExpect(status().isUnauthorized());
        mvc.perform(refreshRequest(victim.refreshToken())).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", victimEmail, "password", PASSWORD)))
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));

        Integer audits = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'USER_SUSPENDED' "
                + "AND entity_id = ? AND actor_user_id = ?", Integer.class, String.valueOf(victimId), admin.getId());
        assertThat(audits).isEqualTo(1);

        mvc.perform(authed(patch("/api/v1/admin/users/" + victimId + "/status"), adminToken)
                        .contentType("application/json").content(body(Map.of("status", "ACTIVE"))))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        login(victimEmail, PASSWORD);

        mvc.perform(authed(patch("/api/v1/admin/users/" + admin.getId() + "/status"), adminToken)
                        .contentType("application/json").content(body(Map.of("status", "SUSPENDED"))))
                .andExpect(status().isConflict());
    }
}
