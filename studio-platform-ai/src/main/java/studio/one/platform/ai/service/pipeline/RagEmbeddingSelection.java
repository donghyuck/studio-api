package studio.one.platform.ai.service.pipeline;

import studio.one.platform.ai.core.embedding.EmbeddingInputType;

public record RagEmbeddingSelection(
        String profileId,
        String provider,
        String model,
        EmbeddingInputType inputType) {

    public RagEmbeddingSelection {
        profileId = normalize(profileId);
        provider = normalize(provider);
        model = normalize(model);
        inputType = inputType == null ? EmbeddingInputType.TEXT : inputType;
    }

    public boolean isLegacyDefault() {
        return profileId == null && provider == null && model == null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
