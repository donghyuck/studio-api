package studio.one.platform.ai.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Optional retrieval controls for RAG chat.
 */
public record ChatRagRetrievalOptionsDto(
        @Min(value = 1, message = "structureTopK must be at least 1")
        @Max(value = 100, message = "structureTopK must be at most 100")
        Integer structureTopK,
        @Min(value = 1, message = "ideaBlockTopK must be at least 1")
        @Max(value = 100, message = "ideaBlockTopK must be at most 100")
        Integer ideaBlockTopK,
        @Min(value = 1, message = "finalTopK must be at least 1")
        @Max(value = 100, message = "finalTopK must be at most 100")
        Integer finalTopK,
        @DecimalMin(value = "0.0", message = "minScore must be at least 0.0")
        @DecimalMax(value = "1.0", message = "minScore must be at most 1.0")
        Double minScore,
        Boolean dedupe,
        Boolean includeDebugChunks,
        @DecimalMin(value = "0.0", message = "distilledScoreBoost must be at least 0.0")
        @DecimalMax(value = "1.0", message = "distilledScoreBoost must be at most 1.0")
        Double distilledScoreBoost,
        Boolean queryExpansionEnabled
) {
}
