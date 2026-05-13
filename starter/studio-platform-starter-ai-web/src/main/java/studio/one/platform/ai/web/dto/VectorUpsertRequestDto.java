package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * DTO describing a batch of documents to upsert into the vector store.
 */
public class VectorUpsertRequestDto {

    @NotEmpty(message = "At least one document must be provided")
    @Valid
    private final List<VectorDocumentDto> documents;

    @JsonCreator
    public VectorUpsertRequestDto(@JsonProperty("documents") List<VectorDocumentDto> documents) {
        this.documents = documents;
    }

    public List<VectorDocumentDto> documents() {
        return documents;
    }

    public List<VectorDocumentDto> getDocuments() {
        return documents;
    }

}