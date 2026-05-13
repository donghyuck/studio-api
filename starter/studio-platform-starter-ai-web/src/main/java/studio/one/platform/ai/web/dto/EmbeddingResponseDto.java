package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO describing the response from an embedding request.
 */
public class EmbeddingResponseDto {

    private final List<EmbeddingVectorDto> vectors;

    @JsonCreator
    public EmbeddingResponseDto(@JsonProperty("vectors") List<EmbeddingVectorDto> vectors) {
        this.vectors = vectors;
    }

    public List<EmbeddingVectorDto> vectors() {
        return vectors;
    }

    public List<EmbeddingVectorDto> getVectors() {
        return vectors;
    }

}