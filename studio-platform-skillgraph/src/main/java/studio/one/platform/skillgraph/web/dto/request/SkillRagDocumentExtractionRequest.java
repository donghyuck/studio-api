package studio.one.platform.skillgraph.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillRagDocumentExtractionRequest(
        @Size(max = 100)
        @NotBlank String objectType,
        @Size(max = 200)
        String objectId,
        String mode,
        @Min(1)
        @Max(5000)
        Integer limit,
        Boolean excludeExtracted,
        Boolean generateEmbeddings,
        @Size(max = 100)
        String embeddingProvider,
        @Size(max = 200)
        String embeddingModel,
        @Min(1)
        @Max(4096)
        Integer embeddingDimension,
        @Size(max = 20)
        String candidateExtractorMode) {

    public SkillRagDocumentExtractionRequest(
            String objectType,
            String objectId,
            String mode,
            Integer limit) {
        this(objectType, objectId, mode, limit, null, null, null, null, null, null);
    }
}
