package studio.one.platform.skillgraph.infrastructure.extraction;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.skillgraph.application.service.SkillMatchPolicy;
import studio.one.platform.skillgraph.domain.port.SkillCandidateExtractor;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillEmbeddingPort;

/**
 * LLM 응답을 스킬 후보 추출기.
 */
@Slf4j
public class LlmSkillCandidateExtractor implements SkillCandidateExtractor{

    public static final String DEFAULT_PROMPT = "skill-extraction";

    private static final double DEFAULT_CONFIDENCE = 0.75d;
    private static final int RESPONSE_PREVIEW_LENGTH = 160;
    private static final List<String> CANDIDATE_ARRAY_FIELDS = List.of("skills", "candidates", "results");

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
    public List<ExtractedSkillTerm> extract(String text) {
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
        } catch (SkillCandidateExtractionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Failed to extract skill candidates via LLM: {}", ex.toString());
            throw new SkillCandidateExtractionException("LLM skill candidate extraction failed", ex);
        }
    }

    private List<SkillCandidateExtractor.ExtractedSkillTerm> parseTerms(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new SkillCandidateExtractionException("LLM skill candidate response is empty");
        }
        try {
            List<Map<String, Object>> rows = parseRows(raw);
            List<SkillCandidateExtractor.ExtractedSkillTerm> terms = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                if (terms.size() >= maxTerms) {
                    break;
                }
                String term = text(row.get("term"));
                if (term == null) {
                    continue;
                }
                double confidence = confidence(row.get("confidence"));
                if (confidence < 0.5d) {
                    continue;
                }
                if (!seen.add(term.toLowerCase(java.util.Locale.ROOT))) {
                    continue;
                }
                terms.add(new SkillCandidateExtractor.ExtractedSkillTerm(
                        term,
                        text(row.get("searchText")),
                        text(row.get("skillType")),
                        text(row.get("action")),
                        textList(row.get("technology")),
                        text(row.get("target")),
                        text(row.get("evidenceText")),
                        text(row.get("context")),
                        text(row.get("difficulty")),
                        confidence));
            }
            return terms;
        } catch (Exception ex) {
            log.warn("Failed to parse LLM skill candidate response: responseChars={}, preview={}, error={}",
                    raw.length(), responsePreview(raw), ex.toString());
            throw new SkillCandidateExtractionException("Failed to parse LLM skill candidate response", ex);
        }
    }

    private List<Map<String, Object>> parseRows(String raw) throws Exception {
        JsonNode rows = candidateRows(parseJsonRoot(raw));
        if (rows == null) {
            throw new IllegalArgumentException(
                    "Expected a JSON array or an object containing skills, candidates, or results");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode row : rows) {
            if (row.isObject()) {
                result.add(objectMapper.convertValue(row, new TypeReference<Map<String, Object>>() {
                }));
            }
        }
        return result;
    }

    private JsonNode parseJsonRoot(String raw) throws Exception {
        String normalized = stripFence(raw.trim());
        Exception directFailure;
        try {
            return objectMapper.readTree(normalized);
        } catch (Exception ex) {
            directFailure = ex;
        }
        for (String candidate : embeddedJsonValues(normalized)) {
            try {
                JsonNode root = objectMapper.readTree(candidate);
                if (candidateRows(root) != null) {
                    return root;
                }
            } catch (Exception ignored) {
                // Continue until a complete candidate payload is found.
            }
        }
        throw directFailure;
    }

    private JsonNode candidateRows(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        if (root.isObject()) {
            for (String field : CANDIDATE_ARRAY_FIELDS) {
                JsonNode value = root.get(field);
                if (value != null && value.isArray()) {
                    return value;
                }
            }
        }
        return null;
    }

    private List<String> embeddedJsonValues(String value) {
        List<String> candidates = new ArrayList<>();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '[' && current != '{') {
                continue;
            }
            String candidate = balancedJsonValue(value, index);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    private String balancedJsonValue(String value, int start) {
        Deque<Character> expectedClosings = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                expectedClosings.push('}');
            } else if (current == '[') {
                expectedClosings.push(']');
            } else if (current == '}' || current == ']') {
                if (expectedClosings.isEmpty() || expectedClosings.pop() != current) {
                    return null;
                }
                if (expectedClosings.isEmpty()) {
                    return value.substring(start, index + 1);
                }
            }
        }
        return null;
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

    private String responsePreview(String raw) {
        String normalized = raw.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (normalized.length() <= RESPONSE_PREVIEW_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, RESPONSE_PREVIEW_LENGTH) + "...";
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
        double confidence;
        if (value instanceof Number number) {
            confidence = number.doubleValue();
        } else {
            String text = text(value);
            if (text == null) {
                confidence = DEFAULT_CONFIDENCE;
            } else {
                try {
                    confidence = Double.parseDouble(text);
                } catch (NumberFormatException ex) {
                    confidence = DEFAULT_CONFIDENCE;
                }
            }
        }
        return Math.max(0.0d, Math.min(1.0d, confidence));
    }

    private List<String> textList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::text)
                    .filter(item -> item != null)
                    .toList();
        }
        String text = text(value);
        return text == null ? List.of() : List.of(text);
    }


}
