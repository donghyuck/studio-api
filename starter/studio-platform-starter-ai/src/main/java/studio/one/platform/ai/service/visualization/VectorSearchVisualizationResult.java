package studio.one.platform.ai.service.visualization;

import java.util.List;

public final class VectorSearchVisualizationResult {
    private final QueryPoint query;
    private final List<ResultPoint> results;

    public VectorSearchVisualizationResult(QueryPoint query, List<ResultPoint> results) {
        this.query = query;
        this.results = results == null ? List.of() : List.copyOf(results);
    }

    public QueryPoint query() {
        return query;
    }

    public List<ResultPoint> results() {
        return results;
    }

    public static final class QueryPoint {
        private final String label;
        private final Double x;
        private final Double y;

        public QueryPoint(String label, Double x, Double y) {
            this.label = label;
            this.x = x;
            this.y = y;
        }

        public String label() {
            return label;
        }

        public Double x() {
            return x;
        }

        public Double y() {
            return y;
        }
    }

    public static final class ResultPoint {
        private final String vectorItemId;
        private final String targetType;
        private final String sourceId;
        private final String label;
        private final double x;
        private final double y;
        private final Double similarity;

        public ResultPoint(
                String vectorItemId,
                String targetType,
                String sourceId,
                String label,
                double x,
                double y,
                Double similarity) {
            this.vectorItemId = vectorItemId;
            this.targetType = targetType;
            this.sourceId = sourceId;
            this.label = label;
            this.x = x;
            this.y = y;
            this.similarity = similarity;
        }

        public String vectorItemId() {
            return vectorItemId;
        }

        public String targetType() {
            return targetType;
        }

        public String sourceId() {
            return sourceId;
        }

        public String label() {
            return label;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public Double similarity() {
            return similarity;
        }
    }
}
