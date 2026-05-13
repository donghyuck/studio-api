package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

import studio.one.platform.ai.core.embedding.EmbeddingInputType;

/**
 * DTO describing an embedding request.
 */
public class EmbeddingRequestDto {

    @NotEmpty(message = "At least one text is required for embedding")
    private final List<String> texts;

    private final String provider;

    private final String model;

    private final EmbeddingInputType inputType;

    private final Map<String, Object> metadata;

    @JsonCreator
    public EmbeddingRequestDto(
            @JsonProperty("texts") List<String> texts,
            @JsonProperty("provider") String provider,
            @JsonProperty("model") String model,
            @JsonProperty("inputType") EmbeddingInputType inputType,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {
        this.texts = texts;
        this.provider = provider;
        this.model = model;
        this.inputType = inputType;
        this.metadata = metadata;
    }

    public List<String> texts() {
        return texts;
    }

    public List<String> getTexts() {
        return texts;
    }

    public String provider() {
        return provider;
    }

    public String getProvider() {
        return provider;
    }

    public String model() {
        return model;
    }

    public String getModel() {
        return model;
    }

    public EmbeddingInputType inputType() {
        return inputType;
    }

    public EmbeddingInputType getInputType() {
        return inputType;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

public EmbeddingRequestDto(List<String> texts) {
        this(texts, null, null, null, null);
    }

}