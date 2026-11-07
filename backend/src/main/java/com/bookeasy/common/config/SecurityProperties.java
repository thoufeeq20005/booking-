package com.bookeasy.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param dataEncryptionKey       base64 32-byte key for AES-GCM field encryption and HMAC code hashing
 * @param clientIpXffIndexFromRight 0 = use the socket address; N = take the Nth entry from the right of
 *                                X-Forwarded-For (only set this when every request passes through N trusted proxies)
 */
@ConfigurationProperties(prefix = "bookeasy.security")
public record SecurityProperties(String dataEncryptionKey, int clientIpXffIndexFromRight) {
}
