package studio.one.platform.ai.core.vector.visualization;

import java.util.List;

public record ProjectionPointPage(long totalCount, List<ProjectionPointView> items) {

    public ProjectionPointPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
