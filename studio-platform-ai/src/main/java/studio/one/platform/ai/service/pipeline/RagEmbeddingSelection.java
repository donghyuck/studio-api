package studio.one.platform.ai.service.pipeline;

import studio.one.platform.ai.core.embedding.EmbeddingInputType;

public final class RagEmbeddingSelection {

    private final String profileId;
    private final String provider;
    private final String model;
    private final EmbeddingInputType inputType;

    public RagEmbeddingSelection(
            String profileId,
            String provider,
            String model,
            EmbeddingInputType inputType
    ) {
                profileId = normalize(profileId);
                provider = normalize(provider);
                model = normalize(model);
                inputType = inputType == null ? EmbeddingInputType.TEXT : inputType;
        
        this.profileId = profileId;
        this.provider = provider;
        this.model = model;
        this.inputType = inputType;
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

    public EmbeddingInputType inputType() {
        return inputType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RagEmbeddingSelection)) {
            return false;
        }
        RagEmbeddingSelection that = (RagEmbeddingSelection) o;
        return java.util.Objects.equals(profileId, that.profileId)
                && java.util.Objects.equals(provider, that.provider)
                && java.util.Objects.equals(model, that.model)
                && java.util.Objects.equals(inputType, that.inputType);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(profileId, provider, model, inputType);
    }

    @Override
    public String toString() {
        return "RagEmbeddingSelection[" +
                "profileId=" + profileId + ", " +
                "provider=" + provider + ", " +
                "model=" + model + ", " +
                "inputType=" + inputType +
                "]";
    }

    public boolean isLegacyDefault() {
        return profileId == null && provider == null && model == null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
