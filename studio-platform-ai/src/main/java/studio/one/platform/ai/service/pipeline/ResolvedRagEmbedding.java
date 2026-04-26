package studio.one.platform.ai.service.pipeline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.vector.VectorRecord;

public record ResolvedRagEmbedding(
        EmbeddingPort embeddingPort,
        String profileId,
        String provider,
        String model,
        Integer dimension,
        EmbeddingInputType inputType) {

    public ResolvedRagEmbedding {
        if (embeddingPort == null) {
            throw new IllegalArgumentException("embeddingPort must not be null");
        }
        profileId = normalize(profileId);
        provider = normalize(provider);
        model = normalize(model);
        inputType = inputType == null ? EmbeddingInputType.TEXT : inputType;
    }

    public EmbeddingRequest request(List<String> texts) {
        return new EmbeddingRequest(texts, provider, model, inputType, metadata());
    }

    public Map<String, Object> metadata() {
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, VectorRecord.KEY_EMBEDDING_PROFILE_ID, profileId);
        put(values, VectorRecord.KEY_EMBEDDING_PROVIDER, provider);
        put(values, VectorRecord.KEY_EMBEDDING_MODEL, model);
        put(values, VectorRecord.KEY_EMBEDDING_DIMENSION, dimension);
        put(values, VectorRecord.KEY_EMBEDDING_INPUT_TYPE, inputType.name());
        return Map.copyOf(values);
    }

    private static void put(Map<String, Object> values, String key, Object value) {
        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
            values.put(key, value);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
