package studio.one.platform.documentconvert.application.service;

import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.documentconvert.application.port.out.DocumentConvertJobRepository;
import studio.one.platform.documentconvert.application.port.out.DocumentConvertJobListener;
import studio.one.platform.documentconvert.application.port.out.DocumentConvertStoragePort;
import studio.one.platform.documentconvert.application.port.out.DocumentConvertWorkerClient;
import studio.one.platform.documentconvert.application.result.DocumentConvertJobResult;
import studio.one.platform.documentconvert.domain.model.DocumentConvertJob;
import studio.one.platform.documentconvert.domain.type.DocumentConvertStatus;
import studio.one.platform.documentconvert.domain.type.DocumentFormat;

public class DocumentConvertService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final DocumentConvertJobRepository repository;
    private final DocumentConvertStoragePort storage;
    private final DocumentConvertWorkerClient workerClient;
    private final ObjectMapper objectMapper;
    private final URI callbackBaseUrl;
    private final int maxRetryCount;
    private final Clock clock;
    private final Supplier<List<DocumentConvertJobListener>> listeners;

    public DocumentConvertService(DocumentConvertJobRepository repository, DocumentConvertStoragePort storage,
            DocumentConvertWorkerClient workerClient, ObjectMapper objectMapper, URI callbackBaseUrl,
            int maxRetryCount, Clock clock) {
        this(repository, storage, workerClient, objectMapper, callbackBaseUrl, maxRetryCount, clock, List.of());
    }

    public DocumentConvertService(DocumentConvertJobRepository repository, DocumentConvertStoragePort storage,
            DocumentConvertWorkerClient workerClient, ObjectMapper objectMapper, URI callbackBaseUrl,
            int maxRetryCount, Clock clock, List<DocumentConvertJobListener> listeners) {
        this(repository, storage, workerClient, objectMapper, callbackBaseUrl, maxRetryCount, clock,
                () -> listeners == null ? List.of() : List.copyOf(listeners));
    }

    public DocumentConvertService(DocumentConvertJobRepository repository, DocumentConvertStoragePort storage,
            DocumentConvertWorkerClient workerClient, ObjectMapper objectMapper, URI callbackBaseUrl,
            int maxRetryCount, Clock clock, Supplier<List<DocumentConvertJobListener>> listeners) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.workerClient = Objects.requireNonNull(workerClient, "workerClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.callbackBaseUrl = Objects.requireNonNull(callbackBaseUrl, "callbackBaseUrl");
        this.maxRetryCount = Math.max(0, maxRetryCount);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.listeners = listeners == null ? List::of : listeners;
    }

    public DocumentConvertJobResult create(String sourceFileId, String sourceFormat, String targetFormat,
            Map<String, Object> options, String requestedBy) {
        return createWithJobId("conv-" + UUID.randomUUID(), sourceFileId, sourceFormat, targetFormat,
                options, requestedBy);
    }

    public DocumentConvertJobResult createWithJobId(String jobId, String sourceFileId, String sourceFormat,
            String targetFormat, Map<String, Object> options, String requestedBy) {
        validateOptions(options);
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId must not be blank");
        }
        if (repository.findByJobId(jobId).isPresent()) {
            throw new IllegalArgumentException("document conversion job already exists: " + jobId);
        }
        Instant now = clock.instant();
        DocumentConvertJob job = DocumentConvertJob.pending(jobId.trim(), sourceFileId,
                DocumentFormat.parse(sourceFormat), DocumentFormat.parse(targetFormat), writeOptions(options),
                requestedBy, now);
        repository.save(job);
        return dispatch(job);
    }

    public DocumentConvertJobResult get(String jobId) {
        DocumentConvertJob job = requireJob(jobId);
        URI download = job.status() == DocumentConvertStatus.COMPLETED ? storage.resultDownloadUrl(job) : null;
        return DocumentConvertJobResult.from(job, download);
    }

    public URI download(String jobId) {
        DocumentConvertJob job = requireJob(jobId);
        if (job.status() != DocumentConvertStatus.COMPLETED) {
            throw new IllegalStateException("document conversion is not completed");
        }
        return storage.resultDownloadUrl(job);
    }

    public DocumentConvertJobResult retry(String jobId) {
        DocumentConvertJob job = requireJob(jobId);
        job.prepareRetry(maxRetryCount, clock.instant());
        repository.save(job);
        return dispatch(job);
    }

    public DocumentConvertJobResult cancel(String jobId) {
        DocumentConvertJob job = requireJob(jobId);
        job.markCanceled(clock.instant());
        repository.save(job);
        listeners.get().forEach(listener -> safely(() -> listener.onCanceled(job)));
        return DocumentConvertJobResult.from(job, null);
    }

    public DocumentConvertJobResult callback(String jobId, String status, String resultFileId,
            String errorCode, String errorMessage) {
        DocumentConvertJob job = requireJob(jobId);
        if (job.status().terminal()) {
            return DocumentConvertJobResult.from(job,
                    job.status() == DocumentConvertStatus.COMPLETED ? storage.resultDownloadUrl(job) : null);
        }
        DocumentConvertStatus callbackStatus = DocumentConvertStatus.valueOf(status);
        if (callbackStatus == DocumentConvertStatus.COMPLETED) {
            String expectedResultFileId = storage.resultFileId(job);
            if (!Objects.equals(expectedResultFileId, resultFileId)) {
                throw new IllegalArgumentException("Callback resultFileId does not match the job target");
            }
            job.markCompleted(resultFileId, clock.instant());
        } else if (callbackStatus == DocumentConvertStatus.FAILED) {
            job.markFailed(errorCode, sanitize(errorMessage), clock.instant());
        } else {
            throw new IllegalArgumentException("callback status must be COMPLETED or FAILED");
        }
        repository.save(job);
        if (job.status() == DocumentConvertStatus.COMPLETED) {
            listeners.get().forEach(listener -> safely(() -> listener.onCompleted(job)));
        } else {
            listeners.get().forEach(listener -> safely(() -> listener.onFailed(job)));
        }
        return get(jobId);
    }

    public String storeResult(String jobId, InputStream input) {
        DocumentConvertJob job = requireJob(jobId);
        if (job.status() != DocumentConvertStatus.RUNNING) {
            throw new IllegalStateException("Only running jobs can upload results");
        }
        return storage.storeResult(job, input);
    }

    private DocumentConvertJobResult dispatch(DocumentConvertJob job) {
        try {
            DocumentConvertStoragePort.TransferUrls urls = storage.prepareTransfer(job);
            job.markRunning(clock.instant());
            repository.save(job);
            workerClient.submit(job, urls.sourceUrl(), urls.uploadUrl(), urls.uploadToken(),
                    callbackBaseUrl.resolve("/api/internal/document-conversions/" + job.jobId() + "/callback"),
                    urls.resultFileId(), readOptions(job.optionsJson()));
            return DocumentConvertJobResult.from(job, null);
        } catch (RuntimeException ex) {
            job.markFailed("WORKER_UNAVAILABLE", sanitize(ex.getMessage()), clock.instant());
            repository.save(job);
            listeners.get().forEach(listener -> safely(() -> listener.onFailed(job)));
            return DocumentConvertJobResult.from(job, null);
        }
    }

    private DocumentConvertJob requireJob(String jobId) {
        return repository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown document conversion job: " + jobId));
    }

    private String writeOptions(Map<String, Object> options) {
        try {
            return objectMapper.writeValueAsString(options == null ? Map.of() : options);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid conversion options", ex);
        }
    }

    private Map<String, Object> readOptions(String json) {
        try {
            return json == null || json.isBlank() ? Map.of() : objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("Stored conversion options are invalid", ex);
        }
    }

    private void validateOptions(Map<String, Object> options) {
        if (options == null) {
            return;
        }
        var allowed = java.util.Set.of("pdfEngine", "mainFont", "toc", "numberSections", "standalone", "metadata");
        if (!allowed.containsAll(options.keySet())) {
            throw new IllegalArgumentException("Unsupported conversion option");
        }
        Object metadata = options.get("metadata");
        if (metadata instanceof Map<?, ?> values
                && !java.util.Set.of("title", "author").containsAll(values.keySet())) {
            throw new IllegalArgumentException("Unsupported conversion metadata option");
        }
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "Document conversion failed";
        }
        String sanitized = message.replaceAll("https?://\\S+", "[url]");
        return sanitized.length() <= 1000 ? sanitized : sanitized.substring(0, 1000);
    }

    private void safely(Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException ignored) {
            // Downstream processing is independently retryable and must not alter conversion state.
        }
    }
}
