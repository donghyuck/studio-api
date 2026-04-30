package studio.one.platform.ai.core.vector.visualization;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VectorProjectionPointRepository {

    void deleteByProjectionId(String projectionId);

    void saveAll(List<VectorProjectionPoint> points);

    ProjectionPointPage findPage(String projectionId, String targetType, String clusterId, String keyword, int limit, int offset);

    List<ProjectionPointView> findByVectorItemIds(String projectionId, Collection<String> vectorItemIds);

    Optional<ProjectionPointView> findByVectorItemId(String projectionId, String vectorItemId);
}
