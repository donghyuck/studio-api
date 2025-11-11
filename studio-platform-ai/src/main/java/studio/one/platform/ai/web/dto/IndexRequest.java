package studio.one.platform.ai.web.dto;

import javax.validation.constraints.NotBlank;
import java.util.Map;

public record IndexRequest(
        @NotBlank String documentId,
        @NotBlank String text,
        Map<String, Object> metadata
) {
}
