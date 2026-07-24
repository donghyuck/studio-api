package studio.one.platform.ai.core.rag;

public record RagEmbeddingSelectionInfo(
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        String embeddingDeploymentId,
        String catalogId,
        String embeddingSpaceId) {

    public RagEmbeddingSelectionInfo(
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel) {
        this(embeddingProfileId, embeddingProvider, embeddingModel, null, null, null);
    }

    public RagEmbeddingSelectionInfo {
        embeddingProfileId = normalize(embeddingProfileId);
        embeddingProvider = normalize(embeddingProvider);
        embeddingModel = normalize(embeddingModel);
        embeddingDeploymentId = normalize(embeddingDeploymentId);
        catalogId = normalize(catalogId);
        embeddingSpaceId = normalize(embeddingSpaceId);
    }

    public boolean empty() {
        return embeddingProfileId == null && embeddingProvider == null && embeddingModel == null
                && embeddingDeploymentId == null && catalogId == null && embeddingSpaceId == null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
