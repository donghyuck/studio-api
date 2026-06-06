package studio.one.platform.skillgraph.web.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillRagExtractionRequest(
        @Size(max = 100)
        @NotBlank String objectType,
        @Size(max = 200)
        String objectId,
        @Size(max = 500)
        String q,
        String mode,
        @Size(max = 500)
        List<@NotBlank @Size(max = 200) String> chunkIds,
        Integer limit,
        Boolean excludeExtracted,
        Boolean generateEmbeddings,
        @Size(max = 100)
        String embeddingProvider,
        @Size(max = 200)
        String embeddingModel,
        Integer embeddingDimension) {

    public SkillRagExtractionRequest(
            String objectType,
            String objectId,
            String mode,
            List<String> chunkIds,
            Integer limit) {
        this(objectType, objectId, null, mode, chunkIds, limit, null, null, null, null, null);
    }
}
