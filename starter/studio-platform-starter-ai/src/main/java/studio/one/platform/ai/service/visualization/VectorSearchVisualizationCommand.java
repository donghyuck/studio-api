package studio.one.platform.ai.service.visualization;

import java.util.List;

public record VectorSearchVisualizationCommand(
        String projectionId,
        String query,
        List<String> targetTypes,
        Integer topK,
        Double minScore,
        String embeddingProvider,
        String embeddingModel) {

    public VectorSearchVisualizationCommand {
        targetTypes = targetTypes == null ? List.of() : List.copyOf(targetTypes);
        embeddingProvider = normalize(embeddingProvider);
        embeddingModel = normalize(embeddingModel);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
