package com.bookeasy.notification.channel;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** Works with any SMTP relay (Brevo, Amazon SES, Zoho, …) – see docs/integrations/email.md. */
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final InternetAddress from;

    public SmtpEmailSender(JavaMailSender mailSender, String fromAddress, String fromName) {
        this.mailSender = mailSender;
        try {
            this.from = new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            boolean rich = message.htmlBody() != null;
            MimeMessageHelper helper = new MimeMessageHelper(mime, rich, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            if (rich) {
                helper.setText(message.textBody(), message.htmlBody());
                if (message.qrPng() != null) {
                    // Inline (cid:) image: shows in Gmail, Outlook and phone mail apps without loading anything remote.
                    helper.addInline(QR_CID, new ByteArrayResource(message.qrPng()), "image/png");
                }
            } else {
                helper.setText(message.textBody(), false);
            }
            mailSender.send(mime);
        } catch (MessagingException | MailException e) {
            throw new DeliveryException("SMTP delivery failed", e);
        }
    }
}
