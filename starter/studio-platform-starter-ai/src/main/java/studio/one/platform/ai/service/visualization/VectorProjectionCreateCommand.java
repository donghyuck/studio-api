package studio.one.platform.ai.service.visualization;

import java.util.List;
import java.util.Map;

import studio.one.platform.ai.core.vector.visualization.ProjectionAlgorithm;
import studio.one.platform.ai.core.vector.visualization.ProjectionMode;
import studio.one.platform.ai.core.vector.visualization.ProjectionSamplingStrategy;

public record VectorProjectionCreateCommand(
        String name,
        ProjectionAlgorithm algorithm,
        List<String> targetTypes,
        Map<String, Object> filters,
        ProjectionMode mode,
        Integer sampleSize,
        ProjectionSamplingStrategy samplingStrategy,
        String createdBy) {

    public VectorProjectionCreateCommand {
        targetTypes = targetTypes == null ? List.of() : List.copyOf(targetTypes);
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        mode = mode == null ? ProjectionMode.DETAIL : mode;
        samplingStrategy = samplingStrategy;
    }

    public VectorProjectionCreateCommand(
            String name,
            ProjectionAlgorithm algorithm,
            List<String> targetTypes,
            Map<String, Object> filters,
            String createdBy) {
        this(name, algorithm, targetTypes, filters, ProjectionMode.DETAIL, null, null, createdBy);
    }
}
