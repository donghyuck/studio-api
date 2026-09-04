package studio.one.platform.ai.service.pipeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.RagObjectScope;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.constant.ServiceNames;

public interface RagPipelineService {

    int MAX_AGGREGATE_OBJECT_SCOPES = 64;

    String SERVICE_NAME = ServiceNames.Features.PREFIX + ":ai:rag-pipeline-service";

    String LEGACY_SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":ai:rag-pipelien-service";

    void index(RagIndexRequest request);

    default void index(RagIndexRequest request, RagIndexProgressListener listener) {
        index(request);
    }

    default boolean supportsObjectPartitions() {
        return false;
    }

    default void indexObjectPartition(
            RagIndexRequest request,
            String objectType,
            String objectId,
            String partitionId,
            RagIndexProgressListener listener) {
        throw new UnsupportedOperationException("indexObjectPartition is not implemented");
    }

    List<RagSearchResult> search(RagSearchRequest request);

    List<RagSearchResult> searchByObject(RagSearchRequest request, String objectType, String objectId);

    /**
     * Searches a bounded set of existing object scopes and returns a globally ranked result set.
     * Implementations can override this to perform one provider-native query. The default keeps
     * existing stores compatible and never depends on Team metadata being copied into vectors.
     */
    default List<RagSearchResult> searchByObjects(
            RagSearchRequest request,
            List<RagObjectScope> requestedScopes,
            int maxScopes) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (maxScopes <= 0 || maxScopes > MAX_AGGREGATE_OBJECT_SCOPES) {
            throw new IllegalArgumentException(
                    "maxScopes must be between 1 and " + MAX_AGGREGATE_OBJECT_SCOPES);
        }
        List<RagObjectScope> scopes = requestedScopes == null
                ? List.of()
                : requestedScopes.stream().distinct().toList();
        if (scopes.size() > maxScopes) {
            throw new IllegalArgumentException("object scope count exceeds maxScopes: " + maxScopes);
        }
        List<ScopedResult> candidates = new ArrayList<>();
        for (RagObjectScope scope : scopes) {
            List<RagSearchResult> results;
            if (!scope.partitionIds().isEmpty() && supportsObjectPartitions()) {
                results = searchByObjectPartitions(
                        request, scope.objectType(), scope.objectId(), scope.partitionIds());
            } else {
                results = searchByObject(request, scope.objectType(), scope.objectId());
                if (!scope.partitionIds().isEmpty()) {
                    results = results.stream()
                            .filter(result -> scope.partitionIds().contains(
                                    String.valueOf(result.metadata().get("partitionId"))))
                            .toList();
                }
            }
            if (results != null) {
                results.forEach(result -> candidates.add(new ScopedResult(scope, result)));
            }
        }
        Map<String, RagSearchResult> deduplicated = new LinkedHashMap<>();
        candidates.stream()
                .sorted(Comparator.comparingDouble(
                        (ScopedResult candidate) -> candidate.result().score()).reversed())
                .forEach(candidate -> deduplicated.putIfAbsent(candidate.dedupeKey(), candidate.result()));
        return deduplicated.values().stream().limit(request.topK()).toList();
    }

    default List<RagSearchResult> searchByObjectPartitions(
            RagSearchRequest request,
            String objectType,
            String objectId,
            Set<String> partitionIds) {
        throw new UnsupportedOperationException("searchByObjectPartitions is not implemented");
    }

    List<RagSearchResult> listByObject(String objectType, String objectId, Integer limit);

    default long countByObject(String objectType, String objectId) {
        return listByObject(objectType, objectId, Integer.MAX_VALUE).size();
    }

    default long countByObject(String objectType, String objectId, String query) {
        return listByObject(objectType, objectId, query, 0, Integer.MAX_VALUE).size();
    }

    default void deleteByObject(String objectType, String objectId) {
        throw new UnsupportedOperationException("deleteByObject is not implemented");
    }

    default void deleteObjectPartition(String objectType, String objectId, String partitionId) {
        throw new UnsupportedOperationException("deleteObjectPartition is not implemented");
    }

    default List<RagSearchResult> listByObject(String objectType, String objectId, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit <= 0 ? 50 : limit;
        long requested = (long) safeOffset + safeLimit;
        int fetchLimit = requested > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) requested;
        return listByObject(objectType, objectId, fetchLimit).stream()
                .skip(safeOffset)
                .limit(safeLimit)
                .toList();
    }

    default List<RagSearchResult> listByObject(
            String objectType,
            String objectId,
            String query,
            int offset,
            int limit) {
        return listByObject(objectType, objectId, offset, limit);
    }

    default List<RagSearchResult> listByChunkIds(String objectType, Set<String> chunkIds) {
        throw new UnsupportedOperationException("listByChunkIds is not implemented");
    }

    /**
     * Applies an additive metadata-only patch to vectors already indexed for the object.
     * No index is created when the object has no vectors.
     */
    default int patchMetadataByObject(
            String objectType,
            String objectId,
            Map<String, Object> metadata) {
        throw new UnsupportedOperationException("patchMetadataByObject is not implemented");
    }

    /**
     * Returns diagnostics for the most recent retrieval in the current thread.
     * Calling from a different thread than the one that executed search returns empty.
     */
    Optional<RagRetrievalDiagnostics> latestDiagnostics();

    record ScopedResult(RagObjectScope scope, RagSearchResult result) {

        String dedupeKey() {
            Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
            Object chunkId = metadata.get("documentChunkId");
            if (chunkId == null) {
                chunkId = metadata.get("_documentChunkId");
            }
            if (chunkId == null) {
                chunkId = metadata.get("chunkId");
            }
            String resultId = chunkId == null || chunkId.toString().isBlank()
                    ? result.documentId() + ":" + Integer.toHexString(result.content().hashCode())
                    : chunkId.toString();
            return scope.objectType() + ":" + scope.objectId() + ":" + resultId;
        }
    }
}
