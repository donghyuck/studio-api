package studio.one.platform.ai.service.pipeline;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobFilter;
import studio.one.platform.ai.core.rag.RagIndexJobLog;
import studio.one.platform.ai.core.rag.RagIndexJobPage;
import studio.one.platform.ai.core.rag.RagIndexJobPageRequest;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexJobStep;

public class InMemoryRagIndexJobRepository implements RagIndexJobRepository {

    private final ConcurrentMap<String, RagIndexJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CopyOnWriteArrayList<RagIndexJobLog>> logs = new ConcurrentHashMap<>();

    @Override
    public RagIndexJob save(RagIndexJob job) {
        jobs.put(job.jobId(), job);
        logs.computeIfAbsent(job.jobId(), ignored -> new CopyOnWriteArrayList<>());
        return job;
    }

    @Override
    public Optional<RagIndexJob> findById(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public RagIndexJobPage findAll(RagIndexJobFilter filter, RagIndexJobPageRequest pageable) {
        RagIndexJobFilter effectiveFilter = filter == null ? RagIndexJobFilter.empty() : filter;
        RagIndexJobPageRequest effectivePageable = pageable == null ? RagIndexJobPageRequest.defaults() : pageable;
        List<RagIndexJob> matched = jobs.values().stream()
                .filter(job -> matches(effectiveFilter, job))
                .sorted(Comparator.comparing(RagIndexJob::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
        int fromIndex = Math.min(effectivePageable.offset(), matched.size());
        int toIndex = Math.min(fromIndex + effectivePageable.limit(), matched.size());
        return new RagIndexJobPage(
                matched.subList(fromIndex, toIndex),
                matched.size(),
                effectivePageable.offset(),
                effectivePageable.limit());
    }

    @Override
    public RagIndexJob updateStatus(
            String jobId,
            RagIndexJobStatus status,
            RagIndexJobStep currentStep,
            String errorMessage) {
        return jobs.compute(jobId, (ignored, current) -> {
            RagIndexJob existing = requireJob(jobId, current);
            RagIndexJobStep nextStep = currentStep == null ? existing.currentStep() : currentStep;
            if ((status == RagIndexJobStatus.SUCCEEDED || status == RagIndexJobStatus.WARNING)
                    && nextStep == null) {
                nextStep = RagIndexJobStep.COMPLETED;
            }
            return existing.withStatus(status, nextStep, errorMessage, Instant.now());
        });
    }

    @Override
    public RagIndexJob updateCounts(
            String jobId,
            Integer chunkCount,
            Integer embeddedCount,
            Integer indexedCount,
            Integer warningCount) {
        return jobs.compute(jobId, (ignored, current) -> requireJob(jobId, current)
                .withCounts(chunkCount, embeddedCount, indexedCount, warningCount));
    }

    @Override
    public RagIndexJobLog appendLog(RagIndexJobLog log) {
        logs.computeIfAbsent(log.jobId(), ignored -> new CopyOnWriteArrayList<>()).add(log);
        return log;
    }

    @Override
    public List<RagIndexJobLog> findLogs(String jobId) {
        return List.copyOf(logs.getOrDefault(jobId, new CopyOnWriteArrayList<>()));
    }

    private boolean matches(RagIndexJobFilter filter, RagIndexJob job) {
        return (filter.status() == null || filter.status() == job.status())
                && matches(filter.objectType(), job.objectType())
                && matches(filter.objectId(), job.objectId())
                && matches(filter.documentId(), job.documentId());
    }

    private boolean matches(String expected, String actual) {
        return expected == null || expected.equals(actual);
    }

    private RagIndexJob requireJob(String jobId, RagIndexJob job) {
        if (job == null) {
            throw new NoSuchElementException("RAG index job not found: " + jobId);
        }
        return job;
    }
}
