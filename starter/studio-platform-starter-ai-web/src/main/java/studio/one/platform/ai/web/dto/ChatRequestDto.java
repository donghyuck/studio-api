package studio.one.platform.ai.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * DTO for submitting chat requests.
 */
public record ChatRequestDto(
        String provider,
        String systemPrompt,
        @NotEmpty(message = "At least one chat message is required")
        @Valid List<ChatMessageDto> messages,
        String model,
        Double temperature,
        Double topP,
        Integer topK,
        Integer maxOutputTokens,
        List<String> stopSequences,
        @Valid ChatMemoryOptionsDto memory
) {
    public ChatRequestDto(
            String provider,
            String systemPrompt,
            List<ChatMessageDto> messages,
            String model,
            Double temperature,
            Double topP,
            Integer topK,
            Integer maxOutputTokens,
            List<String> stopSequences) {
        this(provider, systemPrompt, messages, model, temperature, topP, topK, maxOutputTokens, stopSequences, null);
    }
}
