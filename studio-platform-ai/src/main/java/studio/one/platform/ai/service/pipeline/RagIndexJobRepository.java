package studio.one.platform.ai.service.pipeline;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobFilter;
import studio.one.platform.ai.core.rag.RagIndexJobLog;
import studio.one.platform.ai.core.rag.RagIndexJobPage;
import studio.one.platform.ai.core.rag.RagIndexJobPageRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSort;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexJobStep;

public interface RagIndexJobRepository {

    RagIndexJob save(RagIndexJob job);

    Optional<RagIndexJob> findById(String jobId);

    RagIndexJobPage findAll(RagIndexJobFilter filter, RagIndexJobPageRequest pageable);

    default RagIndexJobPage findAll(
            RagIndexJobFilter filter,
            RagIndexJobPageRequest pageable,
            RagIndexJobSort sort) {
        return findAll(filter, pageable);
    }

    RagIndexJob updateStatus(String jobId, RagIndexJobStatus status, RagIndexJobStep currentStep, String errorMessage);

    default RagIndexJob cancelJob(String jobId, String errorMessage) {
        RagIndexJob job = findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("RAG index job not found: " + jobId));
        if (job.status() != RagIndexJobStatus.PENDING && job.status() != RagIndexJobStatus.RUNNING) {
            throw new IllegalStateException("RAG index job can only be cancelled while active: " + jobId);
        }
        return updateStatus(jobId, RagIndexJobStatus.CANCELLED, job.currentStep(), errorMessage);
    }

    RagIndexJob updateCounts(String jobId, Integer chunkCount, Integer embeddedCount, Integer indexedCount, Integer warningCount);

    RagIndexJobLog appendLog(RagIndexJobLog log);

    List<RagIndexJobLog> findLogs(String jobId);
}
