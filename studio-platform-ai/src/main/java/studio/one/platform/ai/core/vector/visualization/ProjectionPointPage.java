package studio.one.platform.ai.core.vector.visualization;

import java.util.List;

public final class ProjectionPointPage {

    private final long totalCount;
    private final List<ProjectionPointView> items;

    public ProjectionPointPage(
            long totalCount,
            List<ProjectionPointView> items
    ) {
                items = items == null ? List.of() : List.copyOf(items);
        
        this.totalCount = totalCount;
        this.items = items;
    }

    public long totalCount() {
        return totalCount;
    }

    public List<ProjectionPointView> items() {
        return items;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProjectionPointPage)) {
            return false;
        }
        ProjectionPointPage that = (ProjectionPointPage) o;
        return totalCount == that.totalCount
                && java.util.Objects.equals(items, that.items);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(totalCount, items);
    }

    @Override
    public String toString() {
        return "ProjectionPointPage[" +
                "totalCount=" + totalCount + ", " +
                "items=" + items +
                "]";
    }
}
