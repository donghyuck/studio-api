package studio.one.platform.ai.core.vector.visualization;

import java.util.List;
import java.util.Map;

public record VectorProjectionScope(
        List<String> targetTypes,
        Map<String, Object> filters) {

    public VectorProjectionScope {
        targetTypes = targetTypes == null ? List.of() : List.copyOf(targetTypes);
        filters = filters == null ? Map.of() : Map.copyOf(filters);
    }

    public boolean isEmpty() {
        return targetTypes.isEmpty() && filters.isEmpty();
    }
}
