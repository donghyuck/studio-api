package studio.one.platform.ai.core.rag;

public record RagEmbeddingSelectionInfo(
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel) {

    public RagEmbeddingSelectionInfo {
        embeddingProfileId = normalize(embeddingProfileId);
        embeddingProvider = normalize(embeddingProvider);
        embeddingModel = normalize(embeddingModel);
    }

    public boolean empty() {
        return embeddingProfileId == null && embeddingProvider == null && embeddingModel == null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
