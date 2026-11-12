package com.bookeasy.notification.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class BrevoEmailSenderTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String start(int status, AtomicReference<String> body, AtomicReference<String> key) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", ex -> {
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(ex.getRequestHeaders().getFirst("api-key"));
            byte[] out = "{\"messageId\":\"<1@brevo>\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void postsTheMessageWithTheApiKeyAndVerifiedSender() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        String url = start(201, body, key);
        new BrevoEmailSender(RestClient.builder(), url, "xkeysib-test", "hello@bookeasy.test", "BookEasy")
                .send(new EmailSender.EmailMessage("asha@example.test", "Your code", "Code: 123456"));

        assertThat(key.get()).isEqualTo("xkeysib-test");
        JsonNode json = new ObjectMapper().readTree(body.get());
        assertThat(json.path("sender").path("email").asText()).isEqualTo("hello@bookeasy.test");
        assertThat(json.path("sender").path("name").asText()).isEqualTo("BookEasy");
        assertThat(json.path("to").get(0).path("email").asText()).isEqualTo("asha@example.test");
        assertThat(json.path("subject").asText()).isEqualTo("Your code");
        assertThat(json.path("textContent").asText()).isEqualTo("Code: 123456");
    }

    @Test
    void rejectedMessagesRaiseDeliveryException() throws Exception {
        String url = start(401, new AtomicReference<>(), new AtomicReference<>());
        BrevoEmailSender sender = new BrevoEmailSender(RestClient.builder(), url, "bad", "hello@bookeasy.test", null);
        assertThatThrownBy(() -> sender.send(new EmailSender.EmailMessage("a@example.test", "s", "b")))
                .isInstanceOf(DeliveryException.class);
    }
}
