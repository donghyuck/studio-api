package studio.one.platform.ai.web.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO representing chat responses returned to API clients.
 */
public record ChatResponseDto(
        List<ChatMessageDto> messages,
        String model,
        Map<String, Object> metadata
) {
}
