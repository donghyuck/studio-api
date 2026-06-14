package studio.one.platform.documentconvert.application.result;

import java.net.URI;
import java.time.Instant;

import studio.one.platform.documentconvert.domain.model.DocumentConvertJob;
import studio.one.platform.documentconvert.domain.type.DocumentConvertStatus;

public record DocumentConvertJobResult(
        String jobId,
        DocumentConvertStatus status,
        String sourceFileId,
        String sourceFormat,
        String targetFormat,
        String resultFileId,
        URI downloadUrl,
        String errorCode,
        String errorMessage,
        int retryCount,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {

    public static DocumentConvertJobResult from(DocumentConvertJob job, URI downloadUrl) {
        return new DocumentConvertJobResult(job.jobId(), job.status(), job.sourceFileId(),
                job.sourceFormat().name().toLowerCase(), job.targetFormat().name().toLowerCase(),
                job.resultFileId(), downloadUrl, job.errorCode(), job.errorMessage(), job.retryCount(),
                job.createdAt(), job.startedAt(), job.completedAt());
    }
}
