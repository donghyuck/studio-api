package studio.one.platform.ai.service.pipeline;

import java.util.List;
import java.util.Optional;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobFilter;
import studio.one.platform.ai.core.rag.RagIndexJobLog;
import studio.one.platform.ai.core.rag.RagIndexJobPage;
import studio.one.platform.ai.core.rag.RagIndexJobPageRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSort;
import studio.one.platform.ai.core.rag.RagEmbeddingSelectionInfo;

public interface RagIndexJobService {

    RagIndexJob createJob(RagIndexJobCreateRequest request);

    default RagIndexJob createJob(
            RagIndexJobCreateRequest request,
            studio.one.platform.ai.core.rag.RagIndexJobSourceRequest sourceRequest) {
        return createJob(request);
    }

    RagIndexJob startJob(String jobId);

    default RagIndexJob cancelJob(String jobId) {
        throw new UnsupportedOperationException("cancelJob is not implemented");
    }

    RagIndexJob retryJob(String jobId);

    Optional<RagIndexJob> getJob(String jobId);

    default Optional<RagEmbeddingSelectionInfo> getEmbeddingSelection(String jobId) {
        return Optional.empty();
    }

    RagIndexJobPage listJobs(RagIndexJobFilter filter, RagIndexJobPageRequest pageable);

    default RagIndexJobPage listJobs(
            RagIndexJobFilter filter,
            RagIndexJobPageRequest pageable,
            RagIndexJobSort sort) {
        return listJobs(filter, pageable);
    }

    List<RagIndexJobLog> getLogs(String jobId);

    default void deleteObjectHistory(String objectType, String objectId) {
    }

    default List<RagIndexJob> latestJobs(String objectType, List<String> objectIds) {
        if (objectType == null || objectType.isBlank() || objectIds == null || objectIds.isEmpty()) {
            return List.of();
        }
        return objectIds.stream()
                .filter(objectId -> objectId != null && !objectId.isBlank())
                .distinct()
                .map(objectId -> listJobs(
                        new RagIndexJobFilter(null, objectType, objectId, null),
                        new RagIndexJobPageRequest(0, 1),
                        RagIndexJobSort.defaults()).jobs().stream().findFirst().orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    RagIndexProgressListener progressListener(String jobId);
}
