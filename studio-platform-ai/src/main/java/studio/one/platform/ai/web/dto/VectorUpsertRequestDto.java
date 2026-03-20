package studio.one.platform.ai.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * DTO describing a batch of documents to upsert into the vector store.
 */
public record VectorUpsertRequestDto(
        @NotEmpty(message = "At least one document must be provided")
        @Valid List<VectorDocumentDto> documents
) {
}
