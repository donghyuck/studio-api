package studio.one.platform.ai.service.cleaning;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.service.prompt.PromptRenderer;

/**
 * LLM-backed text cleaner for noisy extracted document text.
 */
@Slf4j
public class LlmTextCleaner implements TextCleaner {

    private final PromptRenderer promptRenderer;
    private final ChatPort chatPort;
    private final ObjectMapper objectMapper;
    private final String promptName;
    private final int maxInputChars;
    private final boolean failOpen;

    public LlmTextCleaner(PromptRenderer promptRenderer,
            ChatPort chatPort,
            ObjectMapper objectMapper,
            String promptName,
            int maxInputChars,
            boolean failOpen) {
        this.promptRenderer = Objects.requireNonNull(promptRenderer, "promptRenderer");
        this.chatPort = Objects.requireNonNull(chatPort, "chatPort");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.promptName = requireText(promptName, "promptName");
        if (maxInputChars < 1) {
            throw new IllegalArgumentException("maxInputChars must be greater than 0");
        }
        this.maxInputChars = maxInputChars;
        this.failOpen = failOpen;
    }

    @Override
    public TextCleaningResult clean(String text) {
        if (text == null || text.isBlank()) {
            return TextCleaningResult.skipped(text);
        }
        try {
            String prompt = promptRenderer.render(promptName, Map.of("text", trimToLength(text, maxInputChars)));
            ChatResponse response = chatPort.chat(ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(prompt)))
                    .build());
            String raw = response.messages().get(response.messages().size() - 1).content();
            String cleaned = parseCleanText(raw);
            return new TextCleaningResult(cleaned, true, promptName);
        } catch (Exception ex) {
            if (failOpen) {
                log.warn("Failed to clean RAG text via LLM. Falling back to original text. cause={}", ex.toString());
                return new TextCleaningResult(text, false, promptName);
            }
            throw new IllegalStateException("Failed to clean RAG text via LLM", ex);
        }
    }

    private String parseCleanText(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("cleaner response is blank");
        }
        String value = stripFence(raw.trim());
        JsonNode node = objectMapper.readTree(value);
        JsonNode cleanText = node.get("clean_text");
        if (cleanText == null || !cleanText.isTextual() || cleanText.asText().isBlank()) {
            throw new IllegalArgumentException("cleaner response must contain non-blank clean_text");
        }
        return cleanText.asText();
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

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
