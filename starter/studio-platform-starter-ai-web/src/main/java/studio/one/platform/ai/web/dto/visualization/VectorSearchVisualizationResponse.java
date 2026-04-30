package studio.one.platform.ai.web.dto.visualization;

import java.util.List;

public record VectorSearchVisualizationResponse(QueryPoint query, List<ResultPoint> results) {

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
