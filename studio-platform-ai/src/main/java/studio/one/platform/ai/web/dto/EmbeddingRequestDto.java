package studio.one.platform.ai.web.dto;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * DTO describing an embedding request.
 */
public record EmbeddingRequestDto(
        @NotEmpty(message = "At least one text is required for embedding")
        List<String> texts
) {
}
