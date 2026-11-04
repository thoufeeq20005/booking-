package com.bookeasy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import com.bookeasy.auth.support.Identifiers;
import com.bookeasy.auth.support.PasswordPolicy;
import com.bookeasy.auth.twofactor.Base32;
import com.bookeasy.auth.twofactor.Totp;
import com.bookeasy.common.config.SecurityProperties;
import com.bookeasy.common.crypto.CryptoService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.web.ClientIpResolver;
import com.bookeasy.support.Base32Decoder;

class AuthUnitTest {

    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    /** RFC 6238 Appendix B, SHA-1, 8 digits. */
    @ParameterizedTest
    @CsvSource({"59,94287082", "1111111109,07081804", "1111111111,14050471", "1234567890,89005924",
            "2000000000,69279037", "20000000000,65353130"})
    void totpMatchesRfc6238Vectors(long epochSeconds, String expected) {
        assertThat(Totp.codeAt(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(epochSeconds)), 8)).isEqualTo(expected);
    }

    @Test
    void totpAcceptsOneStepOfDriftAndRejectsMore() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        long step = Totp.stepAt(now);
        assertThat(Totp.verify(RFC_SECRET, Totp.codeAt(RFC_SECRET, step - 1, 6), now)).hasValue(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.codeAt(RFC_SECRET, step + 1, 6), now)).hasValue(step + 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.codeAt(RFC_SECRET, step - 2, 6), now)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "12ab56", now)).isEmpty();
    }

    @Test
    void base32MatchesRfc4648AndRoundTrips() {
        assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        assertThat(Base32.encode("f".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MY");
        assertThat(Base32Decoder.decode(Base32.encode(RFC_SECRET))).isEqualTo(RFC_SECRET);
    }

    @ParameterizedTest
    @CsvSource({"9876543210,+919876543210", "+91 98765 43210,+919876543210", "09876543210,+919876543210",
            "919876543210,+919876543210", "(987) 654-3210,+919876543210"})
    void normalisesIndianMobiles(String raw, String expected) {
        assertThat(Identifiers.normalizeIndianMobile(raw)).hasValue(expected);
    }

    @Test
    void rejectsNonIndianOrMalformedMobiles() {
        assertThat(Identifiers.normalizeIndianMobile("5876543210")).isEmpty();
        assertThat(Identifiers.normalizeIndianMobile("98765")).isEmpty();
        assertThat(Identifiers.normalizeIndianMobile("+14155552671")).isEmpty();
        assertThat(Identifiers.normalizeIndianMobile(null)).isEqualTo(Optional.empty());
        assertThat(Identifiers.normalizeEmail("  Asha@Example.COM ")).isEqualTo("asha@example.com");
    }

    @Test
    void passwordPolicy() {
        PasswordPolicy policy = new PasswordPolicy();
        policy.validate("p", "Sunrise2026");
        policy.validate("p", "சூரியன்2026"); // Unicode letters count as letters
        assertThatThrownBy(() -> policy.validate("p", "Short1")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validate("p", "allletters")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validate("p", "12345678")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validate("p", "a1" + "x".repeat(71))).isInstanceOf(ApiException.class);
    }

    @Test
    void cryptoRoundTripsAndDetectsTampering() {
        CryptoService crypto = new CryptoService(
                new SecurityProperties("dGVzdC1vbmx5LWRhdGEtZW5jcnlwdGlvbi1rZXktMzJieXRlcyEhIQ==", 0));
        String enc = crypto.encrypt(RFC_SECRET);
        assertThat(enc).isNotEqualTo(crypto.encrypt(RFC_SECRET)); // random IV
        assertThat(crypto.decrypt(enc)).isEqualTo(RFC_SECRET);

        byte[] raw = java.util.Base64.getDecoder().decode(enc);
        raw[raw.length - 1] ^= 1;
        String tampered = java.util.Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> crypto.decrypt(tampered)).isInstanceOf(IllegalStateException.class);

        assertThat(crypto.hmacHex("123456")).hasSize(64).isNotEqualTo(crypto.sha256Hex("123456"));
        assertThat(crypto.randomDigits(6)).matches("\\d{6}");
        assertThat(crypto.randomToken(32)).hasSize(43).doesNotContain("+", "/", "=");
    }

    @Test
    void cryptoRefusesMissingOrShortKeys() {
        assertThatThrownBy(() -> new CryptoService(new SecurityProperties("", 0)))
                .hasMessageContaining("DATA_ENCRYPTION_KEY");
        assertThatThrownBy(() -> new CryptoService(new SecurityProperties("c2hvcnQ=", 0)))
                .hasMessageContaining("32 bytes");
    }

    @Test
    void clientIpIgnoresForwardedForUnlessProxiesAreTrusted() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.5");
        req.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.9, 76.76.21.1");

        assertThat(new ClientIpResolver(new SecurityProperties("x", 0)).resolve(req)).isEqualTo("10.0.0.5");
        // Two trusted hops (e.g. Vercel then Render): the client is second from the right, and the
        // spoofed left-most entry is ignored.
        assertThat(new ClientIpResolver(new SecurityProperties("x", 2)).resolve(req)).isEqualTo("203.0.113.9");
    }
}
