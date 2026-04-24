package studio.one.platform.ai.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ConversationMessageActionRequestDto(
        @NotBlank(message = "conversationId is required")
        String conversationId,
        @NotBlank(message = "messageId is required")
        String messageId,
        String newConversationId) {
}
