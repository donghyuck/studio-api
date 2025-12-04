package studio.one.platform.ai.service.keyword;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.service.prompt.PromptManager;

/**
 * LLM 기반 키워드 추출기. 입력 텍스트에서 5~10개의 핵심 키워드를 JSON 배열로 받아 파싱한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LlmKeywordExtractor implements KeywordExtractor {

    private static final String TEMPLATE_NAME = "keyword-extraction";
    private static final String FALLBACK_PROMPT = """
            You are a professional keyword extractor.
            Extract 5-10 concise, noun-centric keywords that best represent the following text.
            Respond with a JSON array of strings only (no code fences, no additional commentary).
            """;

    private final PromptManager promptManager;
    private final ChatPort chatPort;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<String> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String systemPrompt = resolvePrompt();
        try {
            ChatResponse response = chatPort.chat(buildRequest(text, systemPrompt));
            String raw = response.messages().get(response.messages().size() - 1).content();
            return parseKeywords(raw);
        } catch (Exception ex) {
            log.warn("Failed to extract keywords via LLM: {}", ex.toString());
            return List.of();
        }
    }

    private String resolvePrompt() {
        try {
            return promptManager.getRawPrompt(TEMPLATE_NAME);
        } catch (Exception ex) {
            log.warn("Failed to load keyword extraction prompt '{}', using fallback. cause={}", TEMPLATE_NAME, ex.toString());
            return FALLBACK_PROMPT;
        }
    }

    private ChatRequest buildRequest(String text, String systemPrompt) {
        return ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(trimToLength(text, 4000))))
                .build();
    }

    private List<String> parseKeywords(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String cleaned = stripFence(raw.trim());
        List<String> parsed = tryParseJson(cleaned);
        if (!parsed.isEmpty()) {
            return parsed;
        }
        return Arrays.stream(cleaned.split("[,\\n]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<String> tryParseJson(String cleaned) {
        try {
            List<String> list = objectMapper.readValue(cleaned, new TypeReference<List<String>>() {});
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String stripFence(String value) {
        if (value.startsWith("```")) {
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
        return value;
    }

    private String trimToLength(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
