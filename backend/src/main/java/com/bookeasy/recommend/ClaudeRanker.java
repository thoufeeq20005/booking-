package com.bookeasy.recommend;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Claude (Anthropic API) as the recommendation ranker: chooses and explains offers from our database, answering with
 * their ids as structured output. Disabled when ANTHROPIC_API_KEY is empty.
 */
@Component
public class ClaudeRanker {

    private static final Logger log = LoggerFactory.getLogger(ClaudeRanker.class);
    /** Models that accept the server-side refusal fallback ("default" mode). */
    private static final java.util.Set<String> FALLBACK_MODELS = java.util.Set.of("claude-opus-5-5", "claude-opus-5", "claude-fable-5-1",
            "claude-sonnet-5-5");

    private final AnthropicClient client;
    private final String model;
    private final ObjectMapper json;

    public ClaudeRanker(@Value("${bookeasy.ai.api-key:}") String apiKey, @Value("${bookeasy.ai.model:claude-opus-5-5}") String model,
                    ObjectMapper json) {
        this.client = apiKey == null || apiKey.isBlank() ? null
                : AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofSeconds(30)).maxRetries(1).build();
        this.model = model;
        this.json = json;
    }

    public String model() {
        return model;
    }

    public boolean enabled() {
        return client != null;
    }

    public Optional<AiRanker.Ranked> rank(List<RecommendationService.Candidate> candidates, String query, String language, Instant now) {
        if (client == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> input = RankingPrompt.input(candidates, query, now);

            MessageCreateParams.Builder builder = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(16000L)
                    .system(RankingPrompt.system(language))
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
                log.warn("Claude declined the recommendation request; using rule-based ranking");
                return Optional.empty();
            }
            String text = response.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text())
                    .reduce("", String::concat);
            return Optional.of(RankingPrompt.parse(json.readTree(text)));
        } catch (AnthropicServiceException e) {
            log.warn("Claude recommendations failed (HTTP {}); using rule-based ranking", e.statusCode());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Claude recommendations failed ({}); using rule-based ranking", e.toString());
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
