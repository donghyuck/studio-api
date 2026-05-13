package studio.one.platform.ai.service.pipeline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.vector.VectorRecord;

public final class ResolvedRagEmbedding {

    private final EmbeddingPort embeddingPort;
    private final String profileId;
    private final String provider;
    private final String model;
    private final Integer dimension;
    private final EmbeddingInputType inputType;

    public ResolvedRagEmbedding(
            EmbeddingPort embeddingPort,
            String profileId,
            String provider,
            String model,
            Integer dimension,
            EmbeddingInputType inputType
    ) {
                if (embeddingPort == null) {
                    throw new IllegalArgumentException("embeddingPort must not be null");
                }
                profileId = normalize(profileId);
                provider = normalize(provider);
                model = normalize(model);
                inputType = inputType == null ? EmbeddingInputType.TEXT : inputType;
        
        this.embeddingPort = embeddingPort;
        this.profileId = profileId;
        this.provider = provider;
        this.model = model;
        this.dimension = dimension;
        this.inputType = inputType;
    }

    public EmbeddingPort embeddingPort() {
        return embeddingPort;
    }

    public String profileId() {
        return profileId;
    }

    public String provider() {
        return provider;
    }

    public String model() {
        return model;
    }

    public Integer dimension() {
        return dimension;
    }

    public EmbeddingInputType inputType() {
        return inputType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResolvedRagEmbedding)) {
            return false;
        }
        ResolvedRagEmbedding that = (ResolvedRagEmbedding) o;
        return java.util.Objects.equals(embeddingPort, that.embeddingPort)
                && java.util.Objects.equals(profileId, that.profileId)
                && java.util.Objects.equals(provider, that.provider)
                && java.util.Objects.equals(model, that.model)
                && java.util.Objects.equals(dimension, that.dimension)
                && java.util.Objects.equals(inputType, that.inputType);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(embeddingPort, profileId, provider, model, dimension, inputType);
    }

    @Override
    public String toString() {
        return "ResolvedRagEmbedding[" +
                "embeddingPort=" + embeddingPort + ", " +
                "profileId=" + profileId + ", " +
                "provider=" + provider + ", " +
                "model=" + model + ", " +
                "dimension=" + dimension + ", " +
                "inputType=" + inputType +
                "]";
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
        if (value != null && (!(value instanceof String) || !((String) value).isBlank())) {
            values.put(key, value);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
