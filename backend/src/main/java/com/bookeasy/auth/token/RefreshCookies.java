package com.bookeasy.auth.token;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.bookeasy.auth.AuthProperties;

/** Builds the HttpOnly refresh-token cookie. JavaScript never sees the refresh token. */
@Component
public class RefreshCookies {

    private final AuthProperties.RefreshCookie config;
    private final Duration ttl;

    public RefreshCookies(AuthProperties properties) {
        this.config = properties.refreshCookie();
        this.ttl = properties.refreshTokenTtl();
    }

    public ResponseCookie create(String rawToken) {
        return base(rawToken).maxAge(ttl).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (config.name().equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(config.name(), value)
                .httpOnly(true)
                .secure(config.secure())
                .sameSite(config.sameSite())
                .path(config.path());
    }
}
