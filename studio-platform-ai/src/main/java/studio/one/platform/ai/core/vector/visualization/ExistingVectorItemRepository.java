package studio.one.platform.ai.core.vector.visualization;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ExistingVectorItemRepository {

    int DEFAULT_MAX_PROJECTION_ITEMS = 1_000;

    List<VectorItem> findItems(List<String> targetTypes, Map<String, Object> filters);

    default long count(VectorProjectionScope scope) {
        return findItems(scope.targetTypes(), scope.filters()).size();
    }

    default List<VectorItem> findItems(
            VectorProjectionScope scope,
            ProjectionSamplingStrategy samplingStrategy,
            int limit) {
        return findItems(scope.targetTypes(), scope.filters()).stream()
                .limit(Math.max(0, limit))
                .toList();
    }

    default List<ProjectionVector> findProjectionVectors(
            VectorProjectionScope scope,
            ProjectionSamplingStrategy samplingStrategy,
            int limit) {
        return findItems(scope, samplingStrategy, limit).stream()
                .map(item -> new ProjectionVector(
                        item.vectorItemId(),
                        null,
                        item.targetType(),
                        item.sourceId(),
                        item.label(),
                        item.metadata(),
                        item.embedding().stream().mapToDouble(Double::doubleValue).toArray(),
                        item.embeddingModel(),
                        item.embeddingDimension(),
                        item.createdAt()))
                .toList();
    }

    Optional<VectorItem> findByVectorItemId(String vectorItemId);

    List<VectorItem> findByVectorItemIds(Collection<String> vectorItemIds);
}
