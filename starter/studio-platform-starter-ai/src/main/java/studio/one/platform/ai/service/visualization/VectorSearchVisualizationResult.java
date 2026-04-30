package studio.one.platform.ai.service.visualization;

import java.util.List;

public record VectorSearchVisualizationResult(QueryPoint query, List<ResultPoint> results) {

    public VectorSearchVisualizationResult {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public record QueryPoint(String label, Double x, Double y) {
    }

    public record ResultPoint(
            String vectorItemId,
            String targetType,
            String sourceId,
            String label,
            double x,
            double y,
            Double similarity) {
    }
}
