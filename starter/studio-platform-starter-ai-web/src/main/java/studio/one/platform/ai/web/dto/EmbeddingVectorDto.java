package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for a single embedding vector.
 */
public class EmbeddingVectorDto {

    private final String referenceId;

    private final List<Double> values;

    @JsonCreator
    public EmbeddingVectorDto(
            @JsonProperty("referenceId") String referenceId,
            @JsonProperty("values") List<Double> values
    ) {
        this.referenceId = referenceId;
        this.values = values;
    }

    public String referenceId() {
        return referenceId;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public List<Double> values() {
        return values;
    }

    public List<Double> getValues() {
        return values;
    }

}