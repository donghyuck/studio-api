package studio.one.platform.ai.core.vector.visualization;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ExistingVectorItemRepository {

    int DEFAULT_MAX_PROJECTION_ITEMS = 1_000;

    List<VectorItem> findItems(List<String> targetTypes, Map<String, Object> filters);

    Optional<VectorItem> findByVectorItemId(String vectorItemId);

    List<VectorItem> findByVectorItemIds(Collection<String> vectorItemIds);
}
