package studio.one.platform.ai.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IndexedWebSourceRefDto(
        @NotBlank @Size(max = 80) String sourceId,
        @Size(max = 80) String revisionId,
        @Size(max = 80) String corpusRevisionId) {

    public IndexedWebSourceRefDto(String sourceId, String revisionId) {
        this(sourceId, revisionId, null);
    }
}
