package studio.one.platform.ai.service.pipeline;

import studio.one.platform.ai.core.embedding.EmbeddingInputType;

public record RagEmbeddingSelection(
        String profileId,
        String provider,
        String model,
        Integer dimension,
        EmbeddingInputType inputType) {

    public RagEmbeddingSelection {
        profileId = normalize(profileId);
        provider = normalize(provider);
        model = normalize(model);
        if (dimension != null && dimension <= 0) {
            throw new IllegalArgumentException("dimension must be greater than zero");
        }
        inputType = inputType == null ? EmbeddingInputType.TEXT : inputType;
    }

    public RagEmbeddingSelection(String profileId, String provider, String model, EmbeddingInputType inputType) {
        this(profileId, provider, model, null, inputType);
    }

    public boolean isLegacyDefault() {
        return profileId == null && provider == null && model == null && dimension == null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
