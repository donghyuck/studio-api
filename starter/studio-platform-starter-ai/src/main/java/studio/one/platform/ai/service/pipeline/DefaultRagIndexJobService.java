package studio.one.platform.ai.service.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobFilter;
import studio.one.platform.ai.core.rag.RagIndexJobLog;
import studio.one.platform.ai.core.rag.RagIndexJobLogCode;
import studio.one.platform.ai.core.rag.RagIndexJobLogLevel;
import studio.one.platform.ai.core.rag.RagIndexJobPage;
import studio.one.platform.ai.core.rag.RagIndexJobPageRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSort;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexJobStep;

public class DefaultRagIndexJobService implements RagIndexJobService {

    private static final int MAX_STORED_REQUESTS = 1_000;

    private final RagIndexJobRepository repository;
    private final RagPipelineService ragPipelineService;
    private final List<RagIndexJobSourceExecutor> sourceExecutors;
    private final ConcurrentMap<String, StoredRequest> requests = new ConcurrentHashMap<>();
    private final Queue<String> requestOrder = new ConcurrentLinkedQueue<>();
    private final Set<String> runningJobs = ConcurrentHashMap.newKeySet();

    public DefaultRagIndexJobService(
            RagIndexJobRepository repository,
            RagPipelineService ragPipelineService) {
        this(repository, ragPipelineService, List.of());
    }

    public DefaultRagIndexJobService(
            RagIndexJobRepository repository,
            RagPipelineService ragPipelineService,
            List<RagIndexJobSourceExecutor> sourceExecutors) {
        this.repository = repository;
        this.ragPipelineService = ragPipelineService;
        this.sourceExecutors = sourceExecutors == null ? List.of() : List.copyOf(sourceExecutors);
    }

    @Override
    public RagIndexJob createJob(RagIndexJobCreateRequest request) {
        return createJob(request, null);
    }

    @Override
    public RagIndexJob createJob(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest) {
        Objects.requireNonNull(request, "request");
        String jobId = UUID.randomUUID().toString();
        RagIndexJob job = RagIndexJob.pending(
                jobId,
                request.objectType(),
                request.objectId(),
                request.documentId(),
                request.sourceType(),
                Instant.now());
        requests.put(jobId, new StoredRequest(request, sourceRequest));
        requestOrder.add(jobId);
        evictStoredRequests();
        return repository.save(job);
    }

    @Override
    public RagIndexJob startJob(String jobId) {
        if (!runningJobs.add(jobId)) {
            throw new IllegalStateException("RAG index job is already running: " + jobId);
        }
        try {
            RagIndexJob job = requireJob(jobId);
            if (job.status() != RagIndexJobStatus.PENDING) {
                throw new IllegalStateException("RAG index job can only be started from PENDING status: " + jobId);
            }
            return runJob(jobId);
        } finally {
            runningJobs.remove(jobId);
        }
    }

    private RagIndexJob runJob(String jobId) {
        StoredRequest storedRequest = requests.get(jobId);
        RagIndexProgressListener listener = progressListener(jobId);
        listener.onStarted();
        if (isCancelled(jobId)) {
            return requireJob(jobId);
        }
        if (storedRequest == null) {
            listener.onError(
                    RagIndexJobStep.EXTRACTING,
                    RagIndexJobLogCode.SOURCE_UNSUPPORTED,
                    "RAG index source is not executable by the default job service",
                    "No RagIndexRequest was attached to job " + jobId);
            return requireJob(jobId);
        }
        try {
            RagIndexJobCreateRequest request = storedRequest.request();
            if (isCancelled(jobId)) {
                return requireJob(jobId);
            }
            if (request.indexRequest() != null) {
                ragPipelineService.index(request.indexRequest(), listener);
            } else {
                RagIndexJobSourceRequest sourceRequest = storedRequest.sourceRequest();
                Optional<RagIndexJobSourceExecutor> sourceExecutor = sourceExecutor(request, sourceRequest);
                if (sourceExecutor.isEmpty()) {
                    listener.onError(
                            RagIndexJobStep.EXTRACTING,
                            RagIndexJobLogCode.SOURCE_UNSUPPORTED,
                            "RAG index source is not executable by the default job service",
                            request.sourceType());
                    return requireJob(jobId);
                }
                if (isCancelled(jobId)) {
                    return requireJob(jobId);
                }
                sourceExecutor.get().execute(requireJob(jobId), request, sourceRequest, listener);
            }
            listener.onCompleted();
        } catch (RuntimeException ex) {
            RagIndexJob current = requireJob(jobId);
            if (current.status() != RagIndexJobStatus.FAILED && current.status() != RagIndexJobStatus.CANCELLED) {
                RagIndexJobStep step = current.currentStep();
                listener.onError(step, codeFor(step), "RAG index failed", ex.getMessage());
            }
        }
        return requireJob(jobId);
    }

    private Optional<RagIndexJobSourceExecutor> sourceExecutor(
            RagIndexJobCreateRequest request,
            RagIndexJobSourceRequest sourceRequest) {
        return sourceExecutors.stream()
                .filter(executor -> executor.supports(request, sourceRequest))
                .findFirst();
    }

    private boolean isCancelled(String jobId) {
        return requireJob(jobId).status() == RagIndexJobStatus.CANCELLED;
    }

    @Override
    public RagIndexJob cancelJob(String jobId) {
        RagIndexJob cancelled = repository.cancelJob(jobId, "RAG index job cancelled");
        repository.appendLog(log(
                jobId,
                RagIndexJobLogLevel.INFO,
                cancelled.currentStep(),
                RagIndexJobLogCode.JOB_CANCELLED,
                "RAG index job cancelled",
                null));
        return cancelled;
    }

    @Override
    public RagIndexJob retryJob(String jobId) {
        if (!runningJobs.add(jobId)) {
            throw new IllegalStateException("RAG index job is already running: " + jobId);
        }
        try {
            RagIndexJob current = requireJob(jobId);
            if (current.status() == RagIndexJobStatus.PENDING || current.status() == RagIndexJobStatus.RUNNING) {
                throw new IllegalStateException("RAG index job cannot be retried while active: " + jobId);
            }
            RagIndexJob job = current.resetForRetry(Instant.now());
            repository.save(job);
            repository.appendLog(log(
                    jobId,
                    RagIndexJobLogLevel.INFO,
                    null,
                    RagIndexJobLogCode.RETRY_REQUESTED,
                    "RAG index retry requested",
                    null));
            return runJob(jobId);
        } finally {
            runningJobs.remove(jobId);
        }
    }

    @Override
    public Optional<RagIndexJob> getJob(String jobId) {
        return repository.findById(jobId);
    }

    @Override
    public RagIndexJobPage listJobs(RagIndexJobFilter filter, RagIndexJobPageRequest pageable) {
        return repository.findAll(filter, pageable);
    }

    @Override
    public RagIndexJobPage listJobs(RagIndexJobFilter filter, RagIndexJobPageRequest pageable, RagIndexJobSort sort) {
        return repository.findAll(filter, pageable, sort);
    }

    @Override
    public List<RagIndexJobLog> getLogs(String jobId) {
        return repository.findLogs(jobId);
    }

    @Override
    public RagIndexProgressListener progressListener(String jobId) {
        return new RepositoryBackedProgressListener(jobId);
    }

    private RagIndexJob requireJob(String jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("RAG index job not found: " + jobId));
    }

    private RagIndexJobLog log(
            String jobId,
            RagIndexJobLogLevel level,
            RagIndexJobStep step,
            RagIndexJobLogCode code,
            String message,
            String detail) {
        return new RagIndexJobLog(
                UUID.randomUUID().toString(),
                jobId,
                level,
                step,
                code,
                message,
                detail,
                Instant.now());
    }

    private RagIndexJobLogCode codeFor(RagIndexJobStep step) {
        if (step == RagIndexJobStep.EMBEDDING) {
            return RagIndexJobLogCode.EMBEDDING_FAILED;
        }
        if (step == RagIndexJobStep.INDEXING) {
            return RagIndexJobLogCode.VECTOR_UPSERT_FAILED;
        }
        return RagIndexJobLogCode.UNKNOWN_ERROR;
    }

    private void evictStoredRequests() {
        while (requests.size() > MAX_STORED_REQUESTS) {
            String oldestJobId = requestOrder.poll();
            if (oldestJobId == null) {
                return;
            }
            Optional<RagIndexJob> oldestJob = repository.findById(oldestJobId);
            if (oldestJob
                    .filter(job -> job.status() != RagIndexJobStatus.PENDING
                            && job.status() != RagIndexJobStatus.RUNNING)
                    .isPresent()) {
                requests.remove(oldestJobId);
            } else {
                requestOrder.add(oldestJobId);
                return;
            }
            if (requests.size() <= MAX_STORED_REQUESTS) {
                return;
            }
        }
    }

    private record StoredRequest(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest) {
    }

    private class RepositoryBackedProgressListener implements RagIndexProgressListener {

        private final String jobId;

        RepositoryBackedProgressListener(String jobId) {
            this.jobId = jobId;
        }

        @Override
        public void onStarted() {
            if (isCancelled()) {
                return;
            }
            repository.updateStatus(jobId, RagIndexJobStatus.RUNNING, RagIndexJobStep.EXTRACTING, null);
            repository.appendLog(log(
                    jobId,
                    RagIndexJobLogLevel.INFO,
                    RagIndexJobStep.EXTRACTING,
                    RagIndexJobLogCode.JOB_STARTED,
                    "RAG index job started",
                    null));
        }

        @Override
        public void onStep(RagIndexJobStep step) {
            if (isCancelled()) {
                return;
            }
            repository.updateStatus(jobId, RagIndexJobStatus.RUNNING, step, null);
            repository.appendLog(log(
                    jobId,
                    RagIndexJobLogLevel.INFO,
                    step,
                    RagIndexJobLogCode.STEP_CHANGED,
                    "RAG index step changed",
                    step == null ? null : step.name()));
        }

        @Override
        public void onChunkCount(int chunkCount) {
            if (isCancelled()) {
                return;
            }
            repository.updateCounts(jobId, chunkCount, null, null, null);
        }

        @Override
        public void onEmbeddedCount(int embeddedCount) {
            if (isCancelled()) {
                return;
            }
            repository.updateCounts(jobId, null, embeddedCount, null, null);
        }

        @Override
        public void onIndexedCount(int indexedCount) {
            if (isCancelled()) {
                return;
            }
            repository.updateCounts(jobId, null, null, indexedCount, null);
        }

        @Override
        public void onInfo(RagIndexJobStep step, String message, String detail) {
            if (isCancelled()) {
                return;
            }
            repository.appendLog(log(
                    jobId,
                    RagIndexJobLogLevel.INFO,
                    step,
                    RagIndexJobLogCode.STEP_CHANGED,
                    message,
                    detail));
        }

        @Override
        public void onWarning(RagIndexJobStep step, RagIndexJobLogCode code, String message, String detail) {
            if (isCancelled()) {
                return;
            }
            RagIndexJob job = requireJob(jobId);
            repository.updateCounts(jobId, null, null, null, job.warningCount() + 1);
            repository.appendLog(log(jobId, RagIndexJobLogLevel.WARN, step, code, message, detail));
        }

        @Override
        public void onError(RagIndexJobStep step, RagIndexJobLogCode code, String message, String detail) {
            if (isCancelled()) {
                return;
            }
            repository.appendLog(log(jobId, RagIndexJobLogLevel.ERROR, step, code, message, detail));
            repository.updateStatus(jobId, RagIndexJobStatus.FAILED, step, detail == null ? message : detail);
        }

        @Override
        public void onCompleted() {
            if (isCancelled()) {
                return;
            }
            RagIndexJob job = requireJob(jobId);
            RagIndexJobStatus finalStatus = job.warningCount() > 0
                    ? RagIndexJobStatus.WARNING
                    : RagIndexJobStatus.SUCCEEDED;
            repository.updateStatus(jobId, finalStatus, RagIndexJobStep.COMPLETED, null);
            repository.appendLog(log(
                    jobId,
                    RagIndexJobLogLevel.INFO,
                    RagIndexJobStep.COMPLETED,
                    RagIndexJobLogCode.JOB_COMPLETED,
                    "RAG index job completed",
                    finalStatus.name()));
        }

        private boolean isCancelled() {
            return DefaultRagIndexJobService.this.isCancelled(jobId);
        }
    }
}
