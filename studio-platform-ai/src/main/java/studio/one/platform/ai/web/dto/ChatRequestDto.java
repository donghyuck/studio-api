package studio.one.platform.ai.web.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * DTO for submitting chat requests.
 */
public record ChatRequestDto(
        @NotEmpty(message = "At least one chat message is required")
        @Valid List<ChatMessageDto> messages,
        String model,
        Double temperature,
        Double topP,
        Integer topK,
        Integer maxOutputTokens,
        List<String> stopSequences
) {
}
