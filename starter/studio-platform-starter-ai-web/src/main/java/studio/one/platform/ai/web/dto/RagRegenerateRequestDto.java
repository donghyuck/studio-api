package studio.one.platform.ai.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RagRegenerateRequestDto(
        @NotBlank(message = "conversationId is required")
        String conversationId,
        @NotNull @Valid
        ChatRagRequestDto rag) {
}
