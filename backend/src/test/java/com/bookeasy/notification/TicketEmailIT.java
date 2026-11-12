package com.bookeasy.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bookeasy.notification.channel.EmailSender.EmailMessage;
import com.bookeasy.support.BookingTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

class TicketEmailIT extends BookingTestBase {

    @Autowired
    NotificationService notifications;

    private List<EmailMessage> waitForEmails(String to) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            notifications.dispatch();
            List<EmailMessage> mine = channels.emailsTo(to);
            if (!mine.isEmpty()) {
                return mine;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No email sent to " + to);
    }

    /** Grey levels of the image, for the QR reader. */
    private static LuminanceSource luminance(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        byte[] px = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[y * w + x] = (byte) ((img.getRGB(x, y) & 0xFF));
            }
        }
        return new LuminanceSource(w, h) {
            @Override
            public byte[] getRow(int y, byte[] row) {
                byte[] r = row != null && row.length >= w ? row : new byte[w];
                System.arraycopy(px, y * w, r, 0, w);
                return r;
            }

            @Override
            public byte[] getMatrix() {
                return px;
            }
        };
    }

    @Test
    void confirmedBookingEmailsAnETicketWithTheBookingPassQr() throws Exception {
        Biz b = approvedClinic("INSTANT");
        Person asha = verifiedCustomer();
        channels.clear();
        JsonNode a = read(book(asha, b, "09:00").andExpect(status().isOk()).andReturn()).path("data");
        String reference = a.path("reference").asText();
        String pass = a.path("qrPayload").asText();

        EmailMessage email = waitForEmails(asha.email()).get(0);
        assertThat(email.subject()).contains("Booking confirmed");
        assertThat(email.htmlBody()).contains(reference).contains("cid:bookeasy-qr").contains("Your booking is confirmed")
                .contains("Dr. Owner").contains("₹473.03").contains("/customer/bookings/" + a.path("id").asLong());
        assertThat(email.textBody()).contains("Booking ID: " + reference).contains("Amount");

        // The attached image is the same QR the app shows: a link to the public pass page.
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(email.qrPng()));
        String scanned = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(luminance(img))))
                .getText();
        assertThat(scanned).endsWith("/pass/" + URLEncoder.encode(pass, StandardCharsets.UTF_8));
        assertThat(email.qrUrl()).endsWith("/api/v1/public/passes/" + URLEncoder.encode(pass, StandardCharsets.UTF_8) + "/qr.png");

        mvc.perform(get("/api/v1/public/passes/{code}/qr.png", pass))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/api/v1/public/passes/{code}/qr.png", "BOOKEASY:" + reference + ":forged"))
                .andExpect(status().isNotFound());
    }
}
