package com.bookeasy.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class AiProviderTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void autoPrefersClaudeThenGeminiAndForcedProvidersNeedTheirKey() {
        assertThat(AiRanker.choose("auto", true, true)).isEqualTo("claude");
        assertThat(AiRanker.choose("auto", false, true)).isEqualTo("gemini");
        assertThat(AiRanker.choose("auto", false, false)).isEqualTo("none");
        assertThat(AiRanker.choose("gemini", true, true)).isEqualTo("gemini");
        assertThat(AiRanker.choose("claude", false, true)).isEqualTo("none");
    }

    @Test
    void geminiAnswersAreReadWithoutThoughtPartsAndBlockedRepliesAreIgnored() throws Exception {
        String ok = """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[
                  {"text":"thinking...","thought":true},
                  {"text":"{\\"intro\\":\\"Good deals nearby\\",\\"picks\\":[{\\"id\\":\\"p1\\",\\"headline\\":\\"Gym deal\\",\\"reason\\":\\"Saves a lot\\"}]}"}]}}]}""";
        AiRanker.Ranked r = GeminiRanker.parseResponse(json.readTree(ok), json).orElseThrow();
        assertThat(r.intro()).isEqualTo("Good deals nearby");
        assertThat(r.picks()).extracting(RecommendationService.Pick::id).containsExactly("p1");

        assertThat(GeminiRanker.parseResponse(json.readTree("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"), json)).isEmpty();
        assertThat(GeminiRanker.parseResponse(json.readTree(
                "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"{\"}]}}]}"), json)).isEmpty();
    }
}
