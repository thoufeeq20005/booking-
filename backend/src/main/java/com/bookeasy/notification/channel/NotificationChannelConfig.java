package com.bookeasy.notification.channel;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Picks one adapter per channel from configuration. Without a provider, sends either go to the log
 * (dev only, {@code bookeasy.notifications.dev-log-delivery=true}) or fail with {@link DeliveryException}.
 * A message is never reported as sent when no provider accepted it.
 */
@Configuration
public class NotificationChannelConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationChannelConfig.class);

    @Bean
    EmailSender emailSender(NotificationProperties props) {
        NotificationProperties.Email cfg = props.email();
        if ("smtp".equalsIgnoreCase(cfg.provider())) {
            require(cfg.smtpHost(), "SMTP_HOST");
            require(cfg.fromAddress(), "EMAIL_FROM_ADDRESS");
            JavaMailSenderImpl mail = new JavaMailSenderImpl();
            mail.setHost(cfg.smtpHost());
            mail.setPort(cfg.smtpPort());
            mail.setUsername(cfg.smtpUsername());
            mail.setPassword(cfg.smtpPassword());
            mail.setDefaultEncoding("UTF-8");
            Properties p = mail.getJavaMailProperties();
            p.put("mail.smtp.auth", String.valueOf(StringUtils.hasText(cfg.smtpUsername())));
            p.put("mail.smtp.starttls.enable", String.valueOf(cfg.smtpStarttls()));
            p.put("mail.smtp.starttls.required", String.valueOf(cfg.smtpStarttls()));
            p.put("mail.smtp.connectiontimeout", "10000");
            p.put("mail.smtp.timeout", "10000");
            p.put("mail.smtp.writetimeout", "10000");
            log.info("Email channel: SMTP via {}", cfg.smtpHost());
            return new SmtpEmailSender(mail, cfg.fromAddress(), cfg.fromName());
        }
        if (StringUtils.hasText(cfg.provider())) {
            throw new IllegalStateException("Unsupported EMAIL_PROVIDER '" + cfg.provider() + "' (supported: smtp)");
        }
        if (props.devLogDelivery()) {
            log.warn("Email channel: no provider configured – messages are LOGGED, not sent (development only)");
            return message -> log.info("[DEV EMAIL] to={} subject={}\n{}", message.to(), message.subject(),
                    message.textBody());
        }
        log.warn("Email channel: no provider configured – email delivery will fail");
        return message -> {
            throw new DeliveryException("No email provider is configured");
        };
    }

    @Bean
    SmsSender smsSender(NotificationProperties props, RestClient.Builder restClientBuilder) {
        NotificationProperties.Sms cfg = props.sms();
        if ("msg91".equalsIgnoreCase(cfg.provider())) {
            require(cfg.apiKey(), "SMS_API_KEY");
            require(cfg.otpTemplateId(), "SMS_OTP_TEMPLATE_ID");
            log.info("SMS channel: MSG91");
            return new Msg91SmsSender(restClientBuilder, cfg.baseUrl(), cfg.apiKey(), cfg.otpTemplateId(),
                    cfg.otpVariable());
        }
        if (StringUtils.hasText(cfg.provider())) {
            throw new IllegalStateException("Unsupported SMS_PROVIDER '" + cfg.provider() + "' (supported: msg91)");
        }
        if (props.devLogDelivery()) {
            log.warn("SMS channel: no provider configured – OTPs are LOGGED, not sent (development only)");
            return (phone, code) -> log.info("[DEV SMS] OTP for {} is {}", phone, code);
        }
        log.warn("SMS channel: no provider configured – OTP delivery will fail");
        return (phone, code) -> {
            throw new DeliveryException("No SMS provider is configured");
        };
    }

    private static void require(String value, String envName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(envName + " must be set for the selected provider");
        }
    }
}
