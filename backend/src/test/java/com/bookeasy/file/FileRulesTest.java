package com.bookeasy.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.text.LocalizedText;
import com.bookeasy.common.text.Slugs;

class FileRulesTest {

    @Test
    void sniffsRealTypesOnly() {
        assertThat(ContentSniffer.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).contains("application/pdf");
        assertThat(ContentSniffer.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).contains("image/jpeg");
        assertThat(ContentSniffer.detect("RIFF\0\0\0\0WEBP".getBytes(StandardCharsets.US_ASCII))).contains("image/webp");
        assertThat(ContentSniffer.detect("RIFF\0\0\0\0WAVE".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ContentSniffer.detect("MZ\u0090\0".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
        assertThat(ContentSniffer.detect(new byte[0])).isEmpty();
    }

    @Test
    void storedNamesArePathFreeAndMatchTheRealType() {
        assertThat(FileService.safeName("C:\\Users\\me\\Aadhaar card.PDF", "application/pdf")).isEqualTo("Aadhaar card.pdf");
        assertThat(FileService.safeName("../../etc/passwd", "image/png")).isEqualTo("passwd.png");
        assertThat(FileService.safeName("photo\u0000\".jpg", "image/jpeg")).isEqualTo("photo.jpg");
        assertThat(FileService.safeName(null, "image/webp")).isEqualTo("document.webp");
        assertThat(FileService.safeName("x".repeat(300) + ".pdf", "application/pdf")).hasSize(124);
    }

    @Test
    void slugsAreAsciiWithFallback() {
        assertThat(Slugs.from("Dr. Rao's Clinic & Café", 60, "x")).isEqualTo("dr-rao-s-clinic-cafe");
        assertThat(Slugs.from("சன்ரைஸ் கிளினிக்", 60, "business")).isEqualTo("business");
        assertThat(Slugs.from("a".repeat(80), 60, "x")).hasSize(60);
    }

    @Test
    void localizedTextNeedsEnglishAndKnownLanguages() {
        assertThat(LocalizedText.clean("name", Map.of("en", "  Gym ", "ta", " "), 80, true)).isEqualTo(Map.of("en", "Gym"));
        assertThat(LocalizedText.clean("d", null, 80, false)).isNull();
        assertThat(LocalizedText.clean("d", Map.of("en", " "), 80, false)).isNull();
        assertThatThrownBy(() -> LocalizedText.clean("name", Map.of("hi", "जिम"), 80, true))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> LocalizedText.clean("name", Map.of("en", "x".repeat(81)), 80, true))
                .isInstanceOf(ApiException.class);
        assertThat(LocalizedText.resolve(Map.of("en", "Gym", "ta", "ஜிம்"), "kn")).isEqualTo("Gym");
    }
}
