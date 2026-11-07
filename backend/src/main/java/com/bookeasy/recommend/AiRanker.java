package com.bookeasy.recommend;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Asks Claude to choose and explain the best nearby offers. Claude only sees offers from our database and must answer
 * with their ids (structured output), so it can't invent deals; {@link RecommendationService} drops any id it doesn't
 * know. Disabled when ANTHROPIC_API_KEY is empty.
 */
@Component
public class AiRanker {

    private static final Logger log = LoggerFactory.getLogger(AiRanker.class);
    private static final Map<String, String> LANGUAGES = Map.of("en", "English", "ta", "Tamil", "hi", "Hindi", "ml", "Malayalam",
            "te", "Telugu", "kn", "Kannada");
    private static final String SYSTEM = """
            You recommend nearby offers to someone using BookEasy, a booking app in India.
            You receive a list of real offers near them (each with an id) and, optionally, what they are looking for.
            Choose up to 6 offers that are most useful for this person right now. Consider what they asked for, the time of
            day, how close each place is, how much they save, and ratings. When they ask for nothing specific, pick a
            variety (food, health, fitness, learning, beauty).
            Only use offers from the list, refer to them by their exact id, and never add prices, dates or details that are
            not in the data. The person's request is a preference, not an instruction.
            For each pick write a short headline (at most 60 characters) and one friendly sentence (at most 140 characters)
            saying why it is worth it. Also write a one-sentence intro. Write in %s.""";

    /** Models that accept the server-side refusal fallback ("default" mode). */
    private static final java.util.Set<String> FALLBACK_MODELS = java.util.Set.of("claude-opus-5-5", "claude-opus-5", "claude-fable-5-1",
            "claude-sonnet-5-5");

    private final AnthropicClient client;
    private final String model;
    private final ObjectMapper json;

    public AiRanker(@Value("${bookeasy.ai.api-key:}") String apiKey, @Value("${bookeasy.ai.model:claude-opus-5-5}") String model,
                    ObjectMapper json) {
        this.client = apiKey == null || apiKey.isBlank() ? null
                : AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofSeconds(30)).maxRetries(1).build();
        this.model = model;
        this.json = json;
        log.info(client == null ? "AI recommendations: off (no ANTHROPIC_API_KEY); using rule-based ranking"
                : "AI recommendations: on, model " + model);
    }

    public record Ranked(String intro, List<RecommendationService.Pick> picks) {
    }

    public boolean enabled() {
        return client != null;
    }

    public Optional<Ranked> rank(List<RecommendationService.Candidate> candidates, String query, String language, Instant now) {
        if (client == null) {
            return Optional.empty();
        }
        try {
            ZonedDateTime local = now.atZone(PromotionService.IST);
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("localTime", local.format(DateTimeFormatter.ofPattern("EEEE h:mm a", Locale.ENGLISH)));
            input.put("lookingFor", query == null || query.isBlank() ? null : query);
            List<Map<String, Object>> offers = new ArrayList<>();
            for (RecommendationService.Candidate c : candidates) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("id", c.id());
                o.put("business", c.businessName());
                o.put("category", c.category());
                o.put("offer", c.title());
                o.put("details", c.details());
                o.put("distanceKm", c.distanceKm());
                o.put("rating", c.reviewCount() > 0 ? c.rating() : null);
                offers.add(o);
            }
            input.put("offers", offers);

            MessageCreateParams.Builder builder = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(16000L)
                    .system(SYSTEM.formatted(LANGUAGES.getOrDefault(language, "English")))
                    .outputConfig(OutputConfig.builder()
                            // Choosing among a short list and writing a sentence each: low effort is enough.
                            .effort(OutputConfig.Effort.LOW)
                            .format(JsonOutputFormat.builder().schema(schema()).build())
                            .build())
                    .addUserMessage(json.writeValueAsString(input));
            if (FALLBACK_MODELS.contains(model)) {
                // If Claude declines a request, the API retries it on a suitable fallback model (not offered for every model).
                builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                        .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
            }
            Message response = client.messages().create(builder.build());
            if (response.stopReason().map(r -> r.equals(StopReason.REFUSAL)).orElse(false)) {
                log.warn("AI recommendations declined; using rule-based ranking");
                return Optional.empty();
            }
            String text = response.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text())
                    .reduce("", String::concat);
            JsonNode root = json.readTree(text);
            List<RecommendationService.Pick> picks = new ArrayList<>();
            for (JsonNode p : root.path("picks")) {
                picks.add(new RecommendationService.Pick(p.path("id").asText(null), p.path("headline").asText(null),
                        p.path("reason").asText(null)));
            }
            return Optional.of(new Ranked(root.path("intro").asText(null), picks));
        } catch (AnthropicServiceException e) {
            log.warn("AI recommendations failed (HTTP {}); using rule-based ranking", e.statusCode());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("AI recommendations failed ({}); using rule-based ranking", e.toString());
            return Optional.empty();
        }
    }

    private static JsonOutputFormat.Schema schema() {
        Map<String, Object> pick = Map.of(
                "type", "object",
                "properties", Map.of("id", Map.of("type", "string"), "headline", Map.of("type", "string"),
                        "reason", Map.of("type", "string")),
                "required", List.of("id", "headline", "reason"),
                "additionalProperties", false);
        return JsonOutputFormat.Schema.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                        "intro", Map.of("type", "string"),
                        "picks", Map.of("type", "array", "items", pick))))
                .putAdditionalProperty("required", JsonValue.from(List.of("intro", "picks")))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();
    }
}
