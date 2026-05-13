package studio.one.platform.ai.service.visualization;

import java.util.List;

public final class VectorSearchVisualizationCommand {
    private final String projectionId;
    private final String query;
    private final List<String> targetTypes;
    private final Integer topK;
    private final Double minScore;

    public VectorSearchVisualizationCommand(
            String projectionId,
            String query,
            List<String> targetTypes,
            Integer topK,
            Double minScore) {
        this.projectionId = projectionId;
        this.query = query;
        this.targetTypes = targetTypes == null ? List.of() : List.copyOf(targetTypes);
        this.topK = topK;
        this.minScore = minScore;
    }

    public String projectionId() {
        return projectionId;
    }

    public String query() {
        return query;
    }

    public List<String> targetTypes() {
        return targetTypes;
    }

    public Integer topK() {
        return topK;
    }

    public Double minScore() {
        return minScore;
    }
}
