package studio.one.application.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class EmbeddingVectorDto {
    private String referenceId;
    private List<Double> values;

    public EmbeddingVectorDto() {
    }

    public EmbeddingVectorDto(String referenceId, List<Double> values) {
        this.referenceId = referenceId;
        this.values = values;
    }

    public String referenceId() {
        return referenceId;
    }

    public List<Double> values() {
        return values;
    }
}
