package studio.one.platform.ai.service.visualization;

import java.util.List;

import studio.one.platform.ai.core.vector.visualization.ProjectionPointPage;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjection;

public interface VectorProjectionService {

    VectorProjection create(VectorProjectionCreateCommand command);

    List<VectorProjection> list(int limit, int offset);

    VectorProjection get(String projectionId);

    ProjectionPointPage points(String projectionId, String targetType, String clusterId, String keyword, int limit, int offset);

    VectorItem item(String vectorItemId);
}
