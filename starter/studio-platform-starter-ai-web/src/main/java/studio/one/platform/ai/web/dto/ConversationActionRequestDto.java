package studio.one.platform.ai.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;

public record ConversationActionRequestDto(
        @NotBlank(message = "conversationId is required")
        String conversationId,
        String messageId,
        String newConversationId,
        String summary,
        @Valid
        ChatRequestDto chat) {
}
