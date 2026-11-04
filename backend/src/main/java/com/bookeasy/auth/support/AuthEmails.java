package com.bookeasy.auth.support;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import com.bookeasy.auth.AuthProperties;
import com.bookeasy.common.config.BookEasyProperties;
import com.bookeasy.notification.channel.EmailSender;
import com.bookeasy.notification.channel.EmailSender.EmailMessage;
import com.bookeasy.user.entity.User;

/** Renders and sends the account emails in the user's preferred language. */
@Component
public class AuthEmails {

    private final EmailSender emailSender;
    private final MessageSource messages;
    private final BookEasyProperties appProperties;
    private final AuthProperties authProperties;

    public AuthEmails(EmailSender emailSender, MessageSource messages, BookEasyProperties appProperties,
                      AuthProperties authProperties) {
        this.emailSender = emailSender;
        this.messages = messages;
        this.appProperties = appProperties;
        this.authProperties = authProperties;
    }

    public void sendVerification(User user, String rawToken) {
        Locale locale = Locale.forLanguageTag(user.getPreferredLanguage());
        String link = link("/verify-email", rawToken);
        long hours = authProperties.emailVerificationTtl().toHours();
        emailSender.send(new EmailMessage(user.getEmail(),
                messages.getMessage("email.verify.subject", null, locale),
                messages.getMessage("email.verify.body", new Object[]{user.getFullName(), link, hours}, locale)));
    }

    public void sendPasswordReset(User user, String rawToken) {
        Locale locale = Locale.forLanguageTag(user.getPreferredLanguage());
        String link = link("/reset-password", rawToken);
        long minutes = authProperties.passwordResetTtl().toMinutes();
        emailSender.send(new EmailMessage(user.getEmail(),
                messages.getMessage("email.reset.subject", null, locale),
                messages.getMessage("email.reset.body", new Object[]{user.getFullName(), link, minutes}, locale)));
    }

    private String link(String path, String token) {
        return appProperties.frontendUrl() + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
