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
import studio.one.platform.ai.core.rag.RagIndexJobSort;
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
        return findAll(filter, pageable, RagIndexJobSort.defaults());
    }

    @Override
    public RagIndexJobPage findAll(
            RagIndexJobFilter filter,
            RagIndexJobPageRequest pageable,
            RagIndexJobSort sort) {
        RagIndexJobFilter effectiveFilter = filter == null ? RagIndexJobFilter.empty() : filter;
        RagIndexJobPageRequest effectivePageable = pageable == null ? RagIndexJobPageRequest.defaults() : pageable;
        RagIndexJobSort effectiveSort = sort == null ? RagIndexJobSort.defaults() : sort;
        List<RagIndexJob> matched = jobs.values().stream()
                .filter(job -> matches(effectiveFilter, job))
                .sorted(comparator(effectiveSort))
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
            if (existing.status() == RagIndexJobStatus.CANCELLED && status != RagIndexJobStatus.CANCELLED) {
                return existing;
            }
            RagIndexJobStep nextStep = currentStep == null ? existing.currentStep() : currentStep;
            if ((status == RagIndexJobStatus.SUCCEEDED || status == RagIndexJobStatus.WARNING)
                    && nextStep == null) {
                nextStep = RagIndexJobStep.COMPLETED;
            }
            return existing.withStatus(status, nextStep, errorMessage, Instant.now());
        });
    }

    @Override
    public RagIndexJob cancelJob(String jobId, String errorMessage) {
        return jobs.compute(jobId, (ignored, current) -> {
            RagIndexJob existing = requireJob(jobId, current);
            if (existing.status() != RagIndexJobStatus.PENDING && existing.status() != RagIndexJobStatus.RUNNING) {
                throw new IllegalStateException("RAG index job can only be cancelled while active: " + jobId);
            }
            return existing.withStatus(
                    RagIndexJobStatus.CANCELLED,
                    existing.currentStep(),
                    errorMessage,
                    Instant.now());
        });
    }

    @Override
    public RagIndexJob updateCounts(
            String jobId,
            Integer chunkCount,
            Integer embeddedCount,
            Integer indexedCount,
            Integer warningCount) {
        return jobs.compute(jobId, (ignored, current) -> {
            RagIndexJob existing = requireJob(jobId, current);
            if (existing.status() == RagIndexJobStatus.CANCELLED) {
                return existing;
            }
            return existing.withCounts(chunkCount, embeddedCount, indexedCount, warningCount);
        });
    }

    @Override
    public RagIndexJobLog appendLog(RagIndexJobLog log) {
        RagIndexJob job = jobs.get(log.jobId());
        if (job != null
                && job.status() == RagIndexJobStatus.CANCELLED
                && log.code() != studio.one.platform.ai.core.rag.RagIndexJobLogCode.JOB_CANCELLED) {
            return log;
        }
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

    private Comparator<RagIndexJob> comparator(RagIndexJobSort sort) {
        boolean descending = sort.direction() == RagIndexJobSort.Direction.DESC;
        Comparator<RagIndexJob> comparator = switch (sort.field()) {
            case STARTED_AT -> comparingInstant(RagIndexJob::startedAt, descending);
            case FINISHED_AT -> comparingInstant(RagIndexJob::finishedAt, descending);
            case STATUS -> comparingText(job -> job.status().name(), descending);
            case CURRENT_STEP -> comparingText(
                    job -> job.currentStep() == null ? null : job.currentStep().name(),
                    descending);
            case OBJECT_TYPE -> comparingText(RagIndexJob::objectType, descending);
            case OBJECT_ID -> comparingText(RagIndexJob::objectId, descending);
            case DOCUMENT_ID -> comparingText(RagIndexJob::documentId, descending);
            case SOURCE_TYPE -> comparingText(RagIndexJob::sourceType, descending);
            case DURATION_MS -> Comparator.comparing(
                    RagIndexJob::durationMs,
                    Comparator.nullsLast(numberComparator(descending)));
            case CREATED_AT -> comparingInstant(RagIndexJob::createdAt, descending);
        };
        return comparator.thenComparing(RagIndexJob::jobId);
    }

    private Comparator<RagIndexJob> comparingInstant(
            java.util.function.Function<RagIndexJob, Instant> extractor,
            boolean descending) {
        return Comparator.comparing(extractor, Comparator.nullsLast(comparator(descending)));
    }

    private Comparator<RagIndexJob> comparingText(
            java.util.function.Function<RagIndexJob, String> extractor,
            boolean descending) {
        return Comparator.comparing(extractor, Comparator.nullsLast(comparator(descending)));
    }

    private <T extends Comparable<? super T>> Comparator<T> comparator(boolean descending) {
        Comparator<T> comparator = Comparator.naturalOrder();
        return descending ? comparator.reversed() : comparator;
    }

    private Comparator<Long> numberComparator(boolean descending) {
        Comparator<Long> comparator = Comparator.naturalOrder();
        return descending ? comparator.reversed() : comparator;
    }

    private RagIndexJob requireJob(String jobId, RagIndexJob job) {
        if (job == null) {
            throw new NoSuchElementException("RAG index job not found: " + jobId);
        }
        return job;
    }
}
