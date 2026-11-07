package com.bookeasy.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bookeasy.support.OrganizationTestBase;

class TwoFactorResetIT extends OrganizationTestBase {

    @Test
    void adminResetSwitchesOffTwoFactorSignsOutAuditsAndNotifies() throws Exception {
        Person user = verifiedCustomer();
        Session session = login(user.email(), PASSWORD);
        jdbc.update("UPDATE users SET two_factor_enabled = TRUE, two_factor_secret_enc = 'x' WHERE id = ?", user.id());
        Person admin = admin();
        String url = "/api/v1/admin/users/" + user.id() + "/two-factor/reset";

        mvc.perform(jsonRequest(post(url), admin.token(), Map.of("reason", "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fieldErrors[0].field").value("reason"));
        mvc.perform(jsonRequest(post(url), user.token(), Map.of("reason", "Lost phone")))
                .andExpect(status().isForbidden());

        channels.clear();
        mvc.perform(jsonRequest(post(url), admin.token(), Map.of("reason", "Lost phone; ID checked on video call")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.twoFactorEnabled").value(false));

        assertThat(jdbc.queryForObject("SELECT two_factor_secret_enc FROM users WHERE id = ?", String.class, user.id()))
                .isNull();
        mvc.perform(me(user.token())).andExpect(status().isUnauthorized());
        mvc.perform(refreshRequest(session.refreshToken())).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT details->>'$.reason' FROM audit_logs WHERE action = 'USER_2FA_RESET' "
                + "AND entity_id = ?", String.class, String.valueOf(user.id()))).isEqualTo("Lost phone; ID checked on video call");
        assertThat(channels.emailsTo(user.email())).singleElement()
                .satisfies(m -> assertThat(m.textBody()).contains("/account"));

        // Already off, and admins can't reset their own.
        mvc.perform(jsonRequest(post(url), admin.token(), Map.of("reason", "again please")))
                .andExpect(jsonPath("$.error.code").value("TWO_FACTOR_NOT_ENABLED"));
        mvc.perform(jsonRequest(post("/api/v1/admin/users/" + admin.id() + "/two-factor/reset"), admin.token(),
                Map.of("reason", "my own account"))).andExpect(status().isConflict());
    }
}
