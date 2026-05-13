package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

/**
 * DTO describing a document to persist in the vector store.
 */
public class VectorDocumentDto {

    @NotBlank
    private final String id;

    @NotBlank
    private final String content;

    private final Map<String, Object> metadata;

    @NotEmpty(message = "Embedding cannot be empty")
    private final List<Double> embedding;

    @JsonCreator
    public VectorDocumentDto(
            @JsonProperty("id") String id,
            @JsonProperty("content") String content,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("embedding") List<Double> embedding
    ) {
        this.id = id;
        this.content = content;
        this.metadata = metadata;
        this.embedding = embedding;
    }

    public String id() {
        return id;
    }

    public String getId() {
        return id;
    }

    public String content() {
        return content;
    }

    public String getContent() {
        return content;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public List<Double> embedding() {
        return embedding;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

}