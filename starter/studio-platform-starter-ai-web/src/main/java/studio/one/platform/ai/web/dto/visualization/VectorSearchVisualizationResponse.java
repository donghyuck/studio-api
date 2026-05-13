package studio.one.platform.ai.web.dto.visualization;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class VectorSearchVisualizationResponse {
    private final QueryPoint query;
    private final List<ResultPoint> results;

    @JsonCreator
    public VectorSearchVisualizationResponse(
            @JsonProperty("query") QueryPoint query,
            @JsonProperty("results") List<ResultPoint> results) {
        this.query = query;
        this.results = results;
    }

    public QueryPoint query() { return query; }
    public List<ResultPoint> results() { return results; }
    public QueryPoint getQuery() { return query; }
    public List<ResultPoint> getResults() { return results; }

    public static class QueryPoint {
        private final String label;
        private final Double x;
        private final Double y;

        @JsonCreator
        public QueryPoint(@JsonProperty("label") String label,
                          @JsonProperty("x") Double x,
                          @JsonProperty("y") Double y) {
            this.label = label;
            this.x = x;
            this.y = y;
        }

        public String label() { return label; }
        public Double x() { return x; }
        public Double y() { return y; }
        public String getLabel() { return label; }
        public Double getX() { return x; }
        public Double getY() { return y; }
    }

    public static class ResultPoint {
        private final String vectorItemId;
        private final String targetType;
        private final String sourceId;
        private final String label;
        private final double x;
        private final double y;
        private final Double similarity;

        @JsonCreator
        public ResultPoint(@JsonProperty("vectorItemId") String vectorItemId,
                           @JsonProperty("targetType") String targetType,
                           @JsonProperty("sourceId") String sourceId,
                           @JsonProperty("label") String label,
                           @JsonProperty("x") double x,
                           @JsonProperty("y") double y,
                           @JsonProperty("similarity") Double similarity) {
            this.vectorItemId = vectorItemId;
            this.targetType = targetType;
            this.sourceId = sourceId;
            this.label = label;
            this.x = x;
            this.y = y;
            this.similarity = similarity;
        }

        public String vectorItemId() { return vectorItemId; }
        public String targetType() { return targetType; }
        public String sourceId() { return sourceId; }
        public String label() { return label; }
        public double x() { return x; }
        public double y() { return y; }
        public Double similarity() { return similarity; }
        public String getVectorItemId() { return vectorItemId; }
        public String getTargetType() { return targetType; }
        public String getSourceId() { return sourceId; }
        public String getLabel() { return label; }
        public double getX() { return x; }
        public double getY() { return y; }
        public Double getSimilarity() { return similarity; }
    }
}
