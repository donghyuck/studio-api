package studio.one.platform.ai.service.visualization;

import java.util.List;
import java.util.Map;

import studio.one.platform.ai.core.vector.visualization.ProjectionAlgorithm;

public record VectorProjectionCreateCommand(
        String name,
        ProjectionAlgorithm algorithm,
        List<String> targetTypes,
        Map<String, Object> filters,
        String createdBy) {

    public VectorProjectionCreateCommand {
        targetTypes = targetTypes == null ? List.of() : List.copyOf(targetTypes);
        filters = filters == null ? Map.of() : Map.copyOf(filters);
    }
}
