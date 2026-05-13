package studio.one.application.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class EmbeddingResponseDto {
    private List<EmbeddingVectorDto> vectors;

    public EmbeddingResponseDto() {
    }

    public EmbeddingResponseDto(List<EmbeddingVectorDto> vectors) {
        this.vectors = vectors;
    }

    public List<EmbeddingVectorDto> vectors() {
        return vectors;
    }
}
