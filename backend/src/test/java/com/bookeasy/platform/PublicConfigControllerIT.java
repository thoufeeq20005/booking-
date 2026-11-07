package com.bookeasy.platform;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.bookeasy.support.IntegrationTest;

@IntegrationTest
class PublicConfigControllerIT {

    @Autowired
    MockMvc mvc;

    @Test
    void returnsSeededPlatformConfigurationFromTheDatabase() throws Exception {
        mvc.perform(get("/api/v1/public/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andExpect(jsonPath("$.data.commissionPercent").value(5.00))
                .andExpect(jsonPath("$.data.supportedLanguages", contains("en", "ta", "hi", "ml", "te", "kn")))
                .andExpect(jsonPath("$.data.defaultLanguage").value("en"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.data.support.opensAt").value("09:00:00"))
                .andExpect(jsonPath("$.data.support.workingDays[0]").value("MON"));
    }

    @Test
    void supportContactsStayUnsetUntilAdminConfiguresThem() throws Exception {
        mvc.perform(get("/api/v1/public/config"))
                .andExpect(jsonPath("$.data.support.email").doesNotExist())
                .andExpect(jsonPath("$.data.support.phone").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void doesNotExposeNonPublicSettings() throws Exception {
        mvc.perform(get("/api/v1/public/config"))
                .andExpect(jsonPath("$.data.retention").doesNotExist())
                .andExpect(jsonPath("$.data['retention.booking_history_years']").doesNotExist());
    }
}
