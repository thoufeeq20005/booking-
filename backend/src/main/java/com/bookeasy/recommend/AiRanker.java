package com.bookeasy.recommend;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Chooses the AI provider for recommendations. AI_PROVIDER=auto (default) uses Claude when ANTHROPIC_API_KEY is set,
 * otherwise Gemini when GEMINI_API_KEY is set, otherwise none (rule-based ranking). "claude" or "gemini" forces one.
 */
@Component
public class AiRanker {

    private static final Logger log = LoggerFactory.getLogger(AiRanker.class);

    /** What a provider returns: an intro and the picks, by offer id (validated by {@link RecommendationService}). */
    public record Ranked(String intro, List<RecommendationService.Pick> picks) {
    }

    private final ClaudeRanker claude;
    private final GeminiRanker gemini;
    private final String provider;

    public AiRanker(ClaudeRanker claude, GeminiRanker gemini, @Value("${bookeasy.ai.provider:auto}") String provider) {
        this.claude = claude;
        this.gemini = gemini;
        this.provider = choose(provider == null ? "auto" : provider.trim().toLowerCase(java.util.Locale.ROOT), claude.enabled(), gemini.enabled());
        log.info(switch (this.provider) {
            case "claude" -> "AI recommendations: on, Claude model " + claude.model();
            case "gemini" -> "AI recommendations: on, Gemini model " + gemini.model();
            default -> "AI recommendations: off (no ANTHROPIC_API_KEY or GEMINI_API_KEY); using rule-based ranking";
        });
    }

    static String choose(String wanted, boolean claudeReady, boolean geminiReady) {
        return switch (wanted) {
            case "claude" -> claudeReady ? "claude" : "none";
            case "gemini" -> geminiReady ? "gemini" : "none";
            default -> claudeReady ? "claude" : geminiReady ? "gemini" : "none";
        };
    }

    public boolean enabled() {
        return !provider.equals("none");
    }

    public Optional<Ranked> rank(List<RecommendationService.Candidate> candidates, String query, List<String> interests, String language,
                                 Instant now) {
        return switch (provider) {
            case "claude" -> claude.rank(candidates, query, interests, language, now);
            case "gemini" -> gemini.rank(candidates, query, interests, language, now);
            default -> Optional.empty();
        };
    }
}
