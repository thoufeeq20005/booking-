package com.bookeasy.notification.channel;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Brevo's transactional email API over HTTPS. Use it where outbound SMTP ports are blocked (Render's free plan). Free
 * tier: 300 emails a day. The sender address must be verified in Brevo. See docs/integrations/email.md.
 */
public class BrevoEmailSender implements EmailSender {

    static final String DEFAULT_BASE_URL = "https://api.brevo.com";

    private final RestClient client;
    private final Map<String, String> sender;

    public BrevoEmailSender(RestClient.Builder builder, String baseUrl, String apiKey, String fromAddress, String fromName) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).defaultHeader("api-key", apiKey).build();
        this.sender = Map.of("email", fromAddress, "name", fromName == null || fromName.isBlank() ? "BookEasy" : fromName);
    }

    @Override
    public void send(EmailMessage message) {
        try {
            client.post().uri("/v3/smtp/email").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("sender", sender, "to", List.of(Map.of("email", message.to())), "subject", message.subject(),
                            "textContent", message.textBody()))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new DeliveryException("Brevo delivery failed", e);
        }
    }
}
