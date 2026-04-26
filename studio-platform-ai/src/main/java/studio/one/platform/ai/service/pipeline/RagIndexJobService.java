package studio.one.platform.ai.service.pipeline;

import java.util.List;
import java.util.Optional;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobFilter;
import studio.one.platform.ai.core.rag.RagIndexJobLog;
import studio.one.platform.ai.core.rag.RagIndexJobPage;
import studio.one.platform.ai.core.rag.RagIndexJobPageRequest;

public interface RagIndexJobService {

    RagIndexJob createJob(RagIndexJobCreateRequest request);

    default RagIndexJob createJob(
            RagIndexJobCreateRequest request,
            studio.one.platform.ai.core.rag.RagIndexJobSourceRequest sourceRequest) {
        return createJob(request);
    }

    RagIndexJob startJob(String jobId);

    RagIndexJob retryJob(String jobId);

    Optional<RagIndexJob> getJob(String jobId);

    RagIndexJobPage listJobs(RagIndexJobFilter filter, RagIndexJobPageRequest pageable);

    List<RagIndexJobLog> getLogs(String jobId);

    RagIndexProgressListener progressListener(String jobId);
}
