package studio.one.platform.skillgraph.web.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record SkillRagChunkExtractionRequest(
        @Size(max = 100)
        @NotBlank String objectType,
        @Size(max = 200)
        @NotBlank String objectId,
        @Size(max = 200)
        String documentId,
        @NotEmpty
        @Size(max = 500)
        List<@NotBlank @Size(max = 200) String> chunkIds) {
}
