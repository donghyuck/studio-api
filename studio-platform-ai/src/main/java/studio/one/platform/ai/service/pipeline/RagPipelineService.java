package studio.one.platform.ai.service.pipeline;

import java.util.List;
import java.util.Optional;

import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.constant.ServiceNames;

public interface RagPipelineService {

    String SERVICE_NAME = ServiceNames.Features.PREFIX + ":ai:rag-pipeline-service";

    String LEGACY_SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":ai:rag-pipelien-service";

    void index(RagIndexRequest request);

    default void index(RagIndexRequest request, RagIndexProgressListener listener) {
        index(request);
    }

    List<RagSearchResult> search(RagSearchRequest request);

    List<RagSearchResult> searchByObject(RagSearchRequest request, String objectType, String objectId);

    List<RagSearchResult> listByObject(String objectType, String objectId, Integer limit);

    default void deleteByObject(String objectType, String objectId) {
        throw new UnsupportedOperationException("deleteByObject is not implemented");
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
            String documentId,
            String query,
            int offset,
            int limit) {
        return listByObject(objectType, objectId, offset, limit);
    }

    /**
     * Returns diagnostics for the most recent retrieval in the current thread.
     * Calling from a different thread than the one that executed search returns empty.
     */
    Optional<RagRetrievalDiagnostics> latestDiagnostics();
}
