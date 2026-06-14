package studio.one.platform.documentconvert.domain.model;

import java.time.Instant;
import java.util.Objects;

import studio.one.platform.documentconvert.domain.type.DocumentConvertStatus;
import studio.one.platform.documentconvert.domain.type.DocumentFormat;

public final class DocumentConvertJob {
    private final Long id;
    private final String jobId;
    private final String sourceFileId;
    private final DocumentFormat sourceFormat;
    private final DocumentFormat targetFormat;
    private DocumentConvertStatus status;
    private final String optionsJson;
    private String resultFileId;
    private String errorCode;
    private String errorMessage;
    private int retryCount;
    private final String requestedBy;
    private final Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant updatedAt;

    public DocumentConvertJob(Long id, String jobId, String sourceFileId, DocumentFormat sourceFormat,
            DocumentFormat targetFormat, DocumentConvertStatus status, String optionsJson, String resultFileId,
            String errorCode, String errorMessage, int retryCount, String requestedBy, Instant createdAt,
            Instant startedAt, Instant completedAt, Instant updatedAt) {
        this.id = id;
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.sourceFileId = Objects.requireNonNull(sourceFileId, "sourceFileId");
        this.sourceFormat = Objects.requireNonNull(sourceFormat, "sourceFormat");
        this.targetFormat = Objects.requireNonNull(targetFormat, "targetFormat");
        this.status = status == null ? DocumentConvertStatus.PENDING : status;
        this.optionsJson = optionsJson;
        this.resultFileId = resultFileId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.retryCount = retryCount;
        this.requestedBy = requestedBy;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
    }

    public static DocumentConvertJob pending(String jobId, String sourceFileId, DocumentFormat sourceFormat,
            DocumentFormat targetFormat, String optionsJson, String requestedBy, Instant now) {
        if (!sourceFormat.canConvertTo(targetFormat)) {
            throw new IllegalArgumentException("unsupported conversion: " + sourceFormat + " -> " + targetFormat);
        }
        return new DocumentConvertJob(null, jobId, sourceFileId, sourceFormat, targetFormat,
                DocumentConvertStatus.PENDING, optionsJson, null, null, null, 0, requestedBy, now, null, null, now);
    }

    public void markRunning(Instant now) {
        require(DocumentConvertStatus.PENDING, "Only pending jobs can run");
        status = DocumentConvertStatus.RUNNING;
        startedAt = now;
        completedAt = null;
        errorCode = null;
        errorMessage = null;
        updatedAt = now;
    }

    public void markCompleted(String resultFileId, Instant now) {
        if (status.terminal()) {
            return;
        }
        require(DocumentConvertStatus.RUNNING, "Only running jobs can complete");
        this.status = DocumentConvertStatus.COMPLETED;
        this.resultFileId = Objects.requireNonNull(resultFileId, "resultFileId");
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void markFailed(String errorCode, String errorMessage, Instant now) {
        if (status.terminal()) {
            return;
        }
        this.status = DocumentConvertStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void markCanceled(Instant now) {
        if (status.terminal()) {
            return;
        }
        status = DocumentConvertStatus.CANCELED;
        completedAt = now;
        updatedAt = now;
    }

    public void prepareRetry(int maxRetryCount, Instant now) {
        require(DocumentConvertStatus.FAILED, "Only failed jobs can retry");
        if (retryCount >= maxRetryCount) {
            throw new IllegalStateException("maximum retry count exceeded");
        }
        retryCount++;
        status = DocumentConvertStatus.PENDING;
        errorCode = null;
        errorMessage = null;
        completedAt = null;
        updatedAt = now;
    }

    private void require(DocumentConvertStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    public Long id() { return id; }
    public String jobId() { return jobId; }
    public String sourceFileId() { return sourceFileId; }
    public DocumentFormat sourceFormat() { return sourceFormat; }
    public DocumentFormat targetFormat() { return targetFormat; }
    public DocumentConvertStatus status() { return status; }
    public String optionsJson() { return optionsJson; }
    public String resultFileId() { return resultFileId; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public int retryCount() { return retryCount; }
    public String requestedBy() { return requestedBy; }
    public Instant createdAt() { return createdAt; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public Instant updatedAt() { return updatedAt; }
}
