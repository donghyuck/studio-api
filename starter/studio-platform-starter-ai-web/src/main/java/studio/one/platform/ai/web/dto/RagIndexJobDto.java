package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexJobStep;

public class RagIndexJobDto {

    private final String jobId;

    private final String objectType;

    private final String objectId;

    private final String documentId;

    private final String sourceType;

    private final String sourceName;

    private final RagIndexJobStatus status;

    private final RagIndexJobStep currentStep;

    private final int chunkCount;

    private final int embeddedCount;

    private final int indexedCount;

    private final int warningCount;

    private final String errorMessage;

    private final Instant createdAt;

    private final Instant startedAt;

    private final Instant finishedAt;

    private final Long durationMs;

    @JsonCreator
    public RagIndexJobDto(
            @JsonProperty("jobId") String jobId,
            @JsonProperty("objectType") String objectType,
            @JsonProperty("objectId") String objectId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("sourceType") String sourceType,
            @JsonProperty("sourceName") String sourceName,
            @JsonProperty("status") RagIndexJobStatus status,
            @JsonProperty("currentStep") RagIndexJobStep currentStep,
            @JsonProperty("chunkCount") int chunkCount,
            @JsonProperty("embeddedCount") int embeddedCount,
            @JsonProperty("indexedCount") int indexedCount,
            @JsonProperty("warningCount") int warningCount,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("startedAt") Instant startedAt,
            @JsonProperty("finishedAt") Instant finishedAt,
            @JsonProperty("durationMs") Long durationMs
    ) {
        this.jobId = jobId;
        this.objectType = objectType;
        this.objectId = objectId;
        this.documentId = documentId;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.status = status;
        this.currentStep = currentStep;
        this.chunkCount = chunkCount;
        this.embeddedCount = embeddedCount;
        this.indexedCount = indexedCount;
        this.warningCount = warningCount;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.durationMs = durationMs;
    }

    public String jobId() {
        return jobId;
    }

    public String getJobId() {
        return jobId;
    }

    public String objectType() {
        return objectType;
    }

    public String getObjectType() {
        return objectType;
    }

    public String objectId() {
        return objectId;
    }

    public String getObjectId() {
        return objectId;
    }

    public String documentId() {
        return documentId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String sourceType() {
        return sourceType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String sourceName() {
        return sourceName;
    }

    public String getSourceName() {
        return sourceName;
    }

    public RagIndexJobStatus status() {
        return status;
    }

    public RagIndexJobStatus getStatus() {
        return status;
    }

    public RagIndexJobStep currentStep() {
        return currentStep;
    }

    public RagIndexJobStep getCurrentStep() {
        return currentStep;
    }

    public int chunkCount() {
        return chunkCount;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public int embeddedCount() {
        return embeddedCount;
    }

    public int getEmbeddedCount() {
        return embeddedCount;
    }

    public int indexedCount() {
        return indexedCount;
    }

    public int getIndexedCount() {
        return indexedCount;
    }

    public int warningCount() {
        return warningCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Long durationMs() {
        return durationMs;
    }

    public Long getDurationMs() {
        return durationMs;
    }

public static RagIndexJobDto from(RagIndexJob job) {
        return new RagIndexJobDto(
                job.jobId(),
                job.objectType(),
                job.objectId(),
                job.documentId(),
                job.sourceType(),
                sourceName(job),
                job.status(),
                job.currentStep(),
                job.chunkCount(),
                job.embeddedCount(),
                job.indexedCount(),
                job.warningCount(),
                job.errorMessage(),
                job.createdAt(),
                job.startedAt(),
                job.finishedAt(),
                job.durationMs());
    }

    private static String sourceName(RagIndexJob job) {
        return job.sourceName() == null ? job.documentId() : job.sourceName();
    }

}