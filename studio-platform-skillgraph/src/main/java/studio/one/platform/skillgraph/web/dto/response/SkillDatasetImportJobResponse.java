package studio.one.platform.skillgraph.web.dto.response;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.SkillDatasetImportJob;
import studio.one.platform.skillgraph.domain.model.SkillDatasetImportJobStatus;

public record SkillDatasetImportJobResponse(
        String jobId,
        String provider,
        String datasetId,
        String datasetName,
        SkillDatasetImportJobStatus status,
        String sourceLocation,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {

    public static SkillDatasetImportJobResponse from(SkillDatasetImportJob job) {
        return new SkillDatasetImportJobResponse(
                job.jobId(),
                job.provider(),
                job.datasetId(),
                job.datasetName(),
                job.status(),
                job.sourceLocation(),
                job.errorMessage(),
                job.createdAt(),
                job.startedAt(),
                job.completedAt()
        );
    }
}