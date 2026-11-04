package com.bookeasy.file;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Identifies a file's real type from its first bytes. The browser-supplied Content-Type and file name
 * are never trusted: a renamed executable must not be stored or served as a PDF.
 */
public final class ContentSniffer {

    public static final String PDF = "application/pdf";
    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String WEBP = "image/webp";

    public static final int HEADER_BYTES = 12;

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_TAG = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private ContentSniffer() {
    }

    public static Optional<String> detect(byte[] head) {
        if (startsWith(head, PDF_MAGIC)) {
            return Optional.of(PDF);
        }
        if (startsWith(head, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (head.length >= 12 && startsWith(head, RIFF) && Arrays.equals(Arrays.copyOfRange(head, 8, 12), WEBP_TAG)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    public static String extension(String contentType) {
        return switch (contentType) {
            case PDF -> "pdf";
            case JPEG -> "jpg";
            case PNG -> "png";
            case WEBP -> "webp";
            default -> "bin";
        };
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
