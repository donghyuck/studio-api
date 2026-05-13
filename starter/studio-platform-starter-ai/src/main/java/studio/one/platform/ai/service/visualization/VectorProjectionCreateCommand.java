package studio.one.platform.ai.service.visualization;

import java.util.List;
import java.util.Map;

import studio.one.platform.ai.core.vector.visualization.ProjectionAlgorithm;

public final class VectorProjectionCreateCommand {
    private final String name;
    private final ProjectionAlgorithm algorithm;
    private final List<String> targetTypes;
    private final Map<String, Object> filters;
    private final String createdBy;

    public VectorProjectionCreateCommand(
            String name,
            ProjectionAlgorithm algorithm,
            List<String> targetTypes,
            Map<String, Object> filters,
            String createdBy) {
        this.name = name;
        this.algorithm = algorithm;
        this.targetTypes = targetTypes == null ? List.of() : List.copyOf(targetTypes);
        this.filters = filters == null ? Map.of() : Map.copyOf(filters);
        this.createdBy = createdBy;
    }

    public String name() {
        return name;
    }

    public ProjectionAlgorithm algorithm() {
        return algorithm;
    }

    public List<String> targetTypes() {
        return targetTypes;
    }

    public Map<String, Object> filters() {
        return filters;
    }

    public String createdBy() {
        return createdBy;
    }
}
