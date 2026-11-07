package com.bookeasy.recommend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Google Gemini as the recommendation ranker (REST generateContent with a JSON response schema). Same instructions and
 * answer format as Claude; disabled when GEMINI_API_KEY is empty.
 */
@Component
public class GeminiRanker {

    private static final Logger log = LoggerFactory.getLogger(GeminiRanker.class);
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    /** A page waits for this answer, so give up quickly and let the rules rank instead. */
    static final Duration TIMEOUT = Duration.ofSeconds(25);

    private final String apiKey;
    private final String model;
    private final String thinkingLevel;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GeminiRanker(@Value("${bookeasy.ai.gemini-api-key:}") String apiKey,
                        @Value("${bookeasy.ai.gemini-model:gemini-3.5-flash-lite}") String model,
                        @Value("${bookeasy.ai.gemini-thinking-level:low}") String thinkingLevel, ObjectMapper json) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.thinkingLevel = thinkingLevel;
        this.json = json;
    }

    public boolean enabled() {
        return !apiKey.isEmpty();
    }

    public String model() {
        return model;
    }

    public Optional<AiRanker.Ranked> rank(List<RecommendationService.Candidate> candidates, String query, String language, Instant now) {
        if (!enabled()) {
            return Optional.empty();
        }
        try {
            HttpResponse<String> res = call(body(candidates, query, language, now, true));
            if (res.statusCode() == 400 && res.body().contains("hinking")) {
                // This model doesn't accept the thinking setting: ask again without it.
                res = call(body(candidates, query, language, now, false));
            }
            if (res.statusCode() != 200) {
                log.warn("Gemini recommendations failed (HTTP {}: {}); using rule-based ranking", res.statusCode(), errorMessage(res.body()));
                return Optional.empty();
            }
            return parseResponse(json.readTree(res.body()), json);
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("Gemini recommendations timed out after {}s; using rule-based ranking", TIMEOUT.toSeconds());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Gemini recommendations failed ({}); using rule-based ranking", e.toString());
            return Optional.empty();
        }
    }

    private HttpResponse<String> call(String body) throws java.io.IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + model + ":generateContent"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private String body(List<RecommendationService.Candidate> candidates, String query, String language, Instant now,
                        boolean withThinking) throws com.fasterxml.jackson.core.JsonProcessingException {
        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("responseMimeType", "application/json");
        generation.put("responseSchema", Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "intro", Map.of("type", "STRING"),
                        "picks", Map.of("type", "ARRAY", "items", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of("id", Map.of("type", "STRING"), "headline", Map.of("type", "STRING"),
                                        "reason", Map.of("type", "STRING")),
                                "required", List.of("id", "headline", "reason")))),
                "required", List.of("intro", "picks")));
        if (withThinking && thinkingLevel != null && !thinkingLevel.isBlank()) {
            generation.put("thinkingConfig", Map.of("thinkingLevel", thinkingLevel));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", RankingPrompt.system(language)))));
        body.put("contents", List.of(Map.of("role", "user",
                "parts", List.of(Map.of("text", json.writeValueAsString(RankingPrompt.input(candidates, query, now)))))));
        body.put("generationConfig", generation);
        return json.writeValueAsString(body);
    }

    /** The answer text (skipping thought parts), or empty when the reply was blocked or cut short. */
    static Optional<AiRanker.Ranked> parseResponse(JsonNode root, ObjectMapper json) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (root.path("promptFeedback").hasNonNull("blockReason")) {
            return Optional.empty();
        }
        JsonNode candidate = root.path("candidates").path(0);
        String finish = candidate.path("finishReason").asText("");
        if (!finish.isEmpty() && !finish.equals("STOP")) {
            return Optional.empty();
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false) && part.has("text")) {
                text.append(part.path("text").asText());
            }
        }
        if (text.length() == 0) {
            return Optional.empty();
        }
        return Optional.of(RankingPrompt.parse(json.readTree(text.toString())));
    }

    private String errorMessage(String body) {
        try {
            String m = json.readTree(body).path("error").path("message").asText("");
            return m.length() > 160 ? m.substring(0, 160) : m;
        } catch (Exception e) {
            return "unreadable error";
        }
    }
}
