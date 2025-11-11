package studio.one.platform.ai.web.dto;

import javax.validation.constraints.NotBlank;

/**
 * DTO representing a chat message exchanged with an AI provider.
 */
public record ChatMessageDto(
        @NotBlank String role,
        @NotBlank String content
) {
}
