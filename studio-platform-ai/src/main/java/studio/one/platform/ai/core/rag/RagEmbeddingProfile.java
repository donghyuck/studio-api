package studio.one.platform.ai.core.rag;

import java.util.List;
import java.util.Map;

import studio.one.platform.ai.core.embedding.EmbeddingInputType;

/**
 * Named RAG embedding selection profile.
 */
public final class RagEmbeddingProfile {

    private final String profileId;
    private final String provider;
    private final String model;
    private final Integer dimension;
    private final List<EmbeddingInputType> supportedInputTypes;
    private final Map<String, Object> metadata;

    public RagEmbeddingProfile(
            String profileId,
            String provider,
            String model,
            Integer dimension,
            List<EmbeddingInputType> supportedInputTypes,
            Map<String, Object> metadata
    ) {
                profileId = normalize(profileId);
                provider = normalize(provider);
                model = normalize(model);
                supportedInputTypes = supportedInputTypes == null || supportedInputTypes.isEmpty()
                        ? List.of(
                                EmbeddingInputType.TEXT,
                                EmbeddingInputType.TABLE_TEXT,
                                EmbeddingInputType.IMAGE_CAPTION,
                                EmbeddingInputType.OCR_TEXT)
                        : List.copyOf(supportedInputTypes);
                metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        
        this.profileId = profileId;
        this.provider = provider;
        this.model = model;
        this.dimension = dimension;
        this.supportedInputTypes = supportedInputTypes;
        this.metadata = metadata;
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

    public List<EmbeddingInputType> supportedInputTypes() {
        return supportedInputTypes;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RagEmbeddingProfile)) {
            return false;
        }
        RagEmbeddingProfile that = (RagEmbeddingProfile) o;
        return java.util.Objects.equals(profileId, that.profileId)
                && java.util.Objects.equals(provider, that.provider)
                && java.util.Objects.equals(model, that.model)
                && java.util.Objects.equals(dimension, that.dimension)
                && java.util.Objects.equals(supportedInputTypes, that.supportedInputTypes)
                && java.util.Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(profileId, provider, model, dimension, supportedInputTypes, metadata);
    }

    @Override
    public String toString() {
        return "RagEmbeddingProfile[" +
                "profileId=" + profileId + ", " +
                "provider=" + provider + ", " +
                "model=" + model + ", " +
                "dimension=" + dimension + ", " +
                "supportedInputTypes=" + supportedInputTypes + ", " +
                "metadata=" + metadata +
                "]";
    }

    public boolean supports(EmbeddingInputType inputType) {
        EmbeddingInputType resolved = inputType == null ? EmbeddingInputType.TEXT : inputType;
        return supportedInputTypes.contains(resolved);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
