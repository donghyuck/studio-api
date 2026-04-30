package studio.one.platform.ai.service.visualization;

import java.util.List;

public record VectorSearchVisualizationCommand(
        String projectionId,
        String query,
        List<String> targetTypes,
        Integer topK,
        Double minScore) {

    public VectorSearchVisualizationCommand {
        targetTypes = targetTypes == null ? List.of() : List.copyOf(targetTypes);
    }
}
