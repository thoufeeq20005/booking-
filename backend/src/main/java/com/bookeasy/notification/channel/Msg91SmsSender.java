package com.bookeasy.notification.channel;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * MSG91 Flow API (DLT template based). The template must be approved in MSG91 and on the DLT portal,
 * with one variable that receives the OTP. See docs/integrations/sms.md.
 */
public class Msg91SmsSender implements SmsSender {

    private final RestClient client;
    private final String templateId;
    private final String otpVariable;

    public Msg91SmsSender(RestClient.Builder builder, String baseUrl, String apiKey, String templateId,
                          String otpVariable) {
        this.client = builder.baseUrl(baseUrl).defaultHeader("authkey", apiKey).build();
        this.templateId = templateId;
        this.otpVariable = otpVariable;
    }

    @Override
    public void sendOtp(String phoneE164, String code) {
        // MSG91 expects the number with country code and without '+'.
        String mobile = phoneE164.startsWith("+") ? phoneE164.substring(1) : phoneE164;
        Map<String, Object> body = Map.of(
                "template_id", templateId,
                "short_url", "0",
                "recipients", List.of(Map.of("mobiles", mobile, otpVariable, code)));
        try {
            client.post().uri("/api/v5/flow").contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new DeliveryException("MSG91 rejected the SMS", e);
        }
    }
}
