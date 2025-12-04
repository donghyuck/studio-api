package studio.one.platform.ai.web.dto;

import javax.validation.constraints.NotBlank;
import java.util.Map;
import java.util.List;

public record IndexRequest(
        @NotBlank String documentId,
        @NotBlank String text,
        Map<String, Object> metadata,
        List<String> keywords,
        Boolean useLlmKeywordExtraction
) {
}
