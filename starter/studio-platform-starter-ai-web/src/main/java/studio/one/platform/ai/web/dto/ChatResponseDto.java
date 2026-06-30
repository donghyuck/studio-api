package studio.one.platform.ai.web.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO representing chat responses returned to API clients.
 */
public record ChatResponseDto(
        List<ChatMessageDto> messages,
        String model,
        Map<String, Object> metadata,
        String answer,
        String content
) {
    public ChatResponseDto(List<ChatMessageDto> messages, String model, Map<String, Object> metadata) {
        this(messages, model, metadata, firstAssistantContent(messages), firstAssistantContent(messages));
    }

    private static String firstAssistantContent(List<ChatMessageDto> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return messages.stream()
                .filter(message -> message != null
                        && message.role() != null
                        && "assistant".equalsIgnoreCase(message.role()))
                .map(ChatMessageDto::content)
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .orElse(null);
    }
}
