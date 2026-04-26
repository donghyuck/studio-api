package studio.one.platform.ai.core.embedding;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Value object describing a batch embedding request.
 */
public final class EmbeddingRequest {

    private final List<String> texts;
    private final String provider;
    private final String model;
    private final EmbeddingInputType inputType;
    private final Map<String, Object> metadata;

    public EmbeddingRequest(List<String> texts) {
        this(texts, null, null, EmbeddingInputType.TEXT, Map.of());
    }

    public EmbeddingRequest(
            List<String> texts,
            String provider,
            String model,
            EmbeddingInputType inputType,
            Map<String, Object> metadata) {
        Objects.requireNonNull(texts, "texts");
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("At least one text must be provided for embedding");
        }
        this.texts = List.copyOf(texts);
        this.provider = normalize(provider);
        this.model = normalize(model);
        this.inputType = inputType == null ? EmbeddingInputType.TEXT : inputType;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public List<String> texts() {
        return Collections.unmodifiableList(texts);
    }

    public String provider() {
        return provider;
    }

    public String model() {
        return model;
    }

    public EmbeddingInputType inputType() {
        return inputType;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
