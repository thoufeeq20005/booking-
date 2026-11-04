package com.bookeasy.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bookeasy.auth.web.AuthWebSupport;
import com.bookeasy.user.entity.RoleCode;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@IntegrationTest
public abstract class AuthTestBase {

    protected static final String PASSWORD = "Sunrise2026";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected RecordingChannels channels;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetSharedState() {
        clock.reset();
        channels.clear();
        jdbc.update("DELETE FROM rate_limit_counters");
    }

    /** A login session as seen by a browser: access token in memory, refresh token in a cookie. */
    public record Session(String accessToken, String refreshToken, JsonNode body) {
    }

    protected static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 12) + "@example.test";
    }

    protected String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    protected MockHttpServletRequestBuilder postJson(String url, Object payload) throws Exception {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(body(payload));
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected Session register(String email) throws Exception {
        MvcResult r = mvc.perform(postJson("/api/v1/auth/register",
                        Map.of("fullName", "Asha Kumar", "email", email, "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        JsonNode node = read(r).path("data").path("session");
        return new Session(node.path("accessToken").asText(), cookieValue(r), node);
    }

    protected Session login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", password)))
                .andExpect(status().isOk()).andReturn();
        JsonNode node = read(r).path("data");
        return new Session(node.path("accessToken").asText(null), cookieValue(r), node);
    }

    /** Creates a user directly with the given roles (for role tests where no self-service flow exists yet). */
    protected User createUser(String email, RoleCode... roles) {
        User u = new User("Test " + roles[0].name(), email, null, "en", Instant.now());
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        for (RoleCode role : roles) {
            u.addRole(role);
        }
        return users.saveAndFlush(u);
    }

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String accessToken) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    protected MockHttpServletRequestBuilder me(String accessToken) {
        return authed(get("/api/v1/account/me"), accessToken);
    }

    protected MockHttpServletRequestBuilder refreshRequest(String refreshToken) {
        return post("/api/v1/auth/refresh").header(AuthWebSupport.CLIENT_HEADER, "web")
                .cookie(new Cookie("be_rt", refreshToken));
    }

    protected static String cookieValue(MvcResult r) {
        Cookie c = r.getResponse().getCookie("be_rt");
        return c == null ? null : c.getValue();
    }
}
