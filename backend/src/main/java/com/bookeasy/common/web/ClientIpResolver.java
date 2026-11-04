package com.bookeasy.common.web;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import com.bookeasy.common.config.SecurityProperties;

/**
 * Resolves the caller's IP for rate limiting and audit logs. X-Forwarded-For is only honoured from the
 * right-hand side, where entries were appended by our own trusted proxies, so clients cannot spoof it.
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private final int xffIndexFromRight;

    public ClientIpResolver(SecurityProperties properties) {
        this.xffIndexFromRight = properties.clientIpXffIndexFromRight();
    }

    public String resolve(HttpServletRequest request) {
        if (log.isDebugEnabled()) {
            // Use this to confirm CLIENT_IP_XFF_INDEX after deploying behind new proxies (docs/deployment.md).
            log.debug("Client IP inputs: remoteAddr={} X-Forwarded-For={}", request.getRemoteAddr(),
                    request.getHeader("X-Forwarded-For"));
        }
        if (xffIndexFromRight > 0) {
            String header = request.getHeader("X-Forwarded-For");
            if (header != null && !header.isBlank()) {
                String[] parts = header.split(",");
                int idx = parts.length - xffIndexFromRight;
                if (idx >= 0) {
                    return truncate(parts[idx].trim());
                }
            }
        }
        return truncate(request.getRemoteAddr());
    }

    private static String truncate(String ip) {
        return ip == null ? null : ip.length() > 45 ? ip.substring(0, 45) : ip;
    }
}
