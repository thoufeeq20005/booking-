package com.bookeasy.appointment;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/** QR images of booking passes, the same code the app shows (a link to the public pass page). */
public final class QrImages {

    private QrImages() {
    }

    /** What the customer's QR holds: {@code <site>/pass/<payload>}, as the app builds it. */
    public static String passLink(String frontendUrl, String payload) {
        String site = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
        return site + "/pass/" + URLEncoder.encode(payload, StandardCharsets.UTF_8);
    }

    /** Black-on-white PNG, {@code size} pixels square including a quiet zone. */
    public static byte[] png(String content, int size) {
        try {
            BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2,
                            EncodeHintType.CHARACTER_SET, "UTF-8"));
            BufferedImage img = new BufferedImage(m.getWidth(), m.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < m.getHeight(); y++) {
                for (int x = 0; x < m.getWidth(); x++) {
                    img.setRGB(x, y, m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (WriterException e) {
            throw new IllegalArgumentException("Can't draw a QR code for this content", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
