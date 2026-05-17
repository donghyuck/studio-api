package studio.one.platform.skillgraph.application.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.skillgraph.domain.port.SkillCandidateExtractor;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillEmbeddingPort;

/**
 * LLM 응답을 스킬 후보 추천기로 사용하는 SkillExtractionService 구현체.
 */
@Slf4j
public class LlmSkillCandidateExtractor extends AbstractSkillCandidateExtractor {

    public static final String DEFAULT_PROMPT = "skill-extraction";

    private static final double DEFAULT_CONFIDENCE = 0.75d;

    private final PromptRenderer promptRenderer;
    private final ChatPort chatPort;
    private final ObjectMapper objectMapper;
    private final String promptName;
    private final int maxTerms;
    private final int maxInputChars;
    private final Integer maxOutputTokens;
    private final Double temperature;

    public LlmSkillCandidateExtractor(
            SkillCandidateStore store,
            SkillDictionaryStore dictionaryStore,
            SkillEmbeddingPort embeddingPort,
            SkillMatchPolicy matchPolicy,
            PromptRenderer promptRenderer,
            ChatPort chatPort,
            ObjectMapper objectMapper,
            String promptName,
            int maxTerms,
            int maxInputChars,
            Integer maxOutputTokens,
            Double temperature) {
        super(store, dictionaryStore, embeddingPort, matchPolicy);
        this.promptRenderer = Objects.requireNonNull(promptRenderer, "promptRenderer");
        this.chatPort = Objects.requireNonNull(chatPort, "chatPort");
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.promptName = promptName == null || promptName.isBlank() ? DEFAULT_PROMPT : promptName.trim();
        this.maxTerms = Math.max(1, maxTerms);
        this.maxInputChars = Math.max(1, maxInputChars);
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
    }

    @Override
    protected List<SkillCandidateExtractor.ExtractedSkillTerm> recommendTerms(String text) {
        try {
            String prompt = promptRenderer.render(promptName, Map.of(
                    "text", trimToLength(text, maxInputChars),
                    "maxTerms", maxTerms));
            ChatResponse response = chatPort.chat(ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(prompt)))
                    .maxOutputTokens(maxOutputTokens)
                    .temperature(temperature)
                    .build());
            return parseTerms(lastAssistantContent(response));
        } catch (RuntimeException ex) {
            log.warn("Failed to recommend skill candidates via LLM: {}", ex.toString());
            return List.of();
        }
    }

    private List<SkillCandidateExtractor.ExtractedSkillTerm> parseTerms(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(
                    stripFence(raw.trim()),
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            List<SkillCandidateExtractor.ExtractedSkillTerm> terms = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                if (terms.size() >= maxTerms) {
                    break;
                }
                String term = text(row.get("term"));
                if (term == null || !seen.add(term.toLowerCase(java.util.Locale.ROOT))) {
                    continue;
                }
                terms.add(new SkillCandidateExtractor.ExtractedSkillTerm(term, confidence(row.get("confidence"))));
            }
            return terms;
        } catch (Exception ex) {
            log.warn("Failed to parse LLM skill candidate response: {}", ex.toString());
            return List.of();
        }
    }

    private String lastAssistantContent(ChatResponse response) {
        if (response == null || response.messages().isEmpty()) {
            return "";
        }
        return response.messages().get(response.messages().size() - 1).content();
    }

    private String stripFence(String value) {
        if (!value.startsWith("```")) {
            return value;
        }
        int firstNewline = value.indexOf('\n');
        if (firstNewline > 0) {
            value = value.substring(firstNewline + 1);
        }
        int fence = value.lastIndexOf("```");
        if (fence >= 0) {
            value = value.substring(0, fence);
        }
        return value.trim();
    }

    private String trimToLength(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private double confidence(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = text(value);
        if (text == null) {
            return DEFAULT_CONFIDENCE;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return DEFAULT_CONFIDENCE;
        }
    }
}
