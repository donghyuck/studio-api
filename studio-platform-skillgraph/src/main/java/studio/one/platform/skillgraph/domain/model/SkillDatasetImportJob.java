package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record SkillDatasetImportJob(
        String jobId,
        String provider,
        String datasetId,
        String datasetName,
        String version,
        String language,
        String sourceLocation,
        SkillDatasetImportJobStatus status,
        long totalRows,
        long processedRows,
        long createdConcepts,
        long createdRelations,
        long failedRows,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {
    public SkillDatasetImportJob running(Instant now) {
        return copy(SkillDatasetImportJobStatus.RUNNING, startedAt == null ? now : startedAt, null, null);
    }

    public SkillDatasetImportJob completed(Instant now) {
        return copy(SkillDatasetImportJobStatus.COMPLETED, startedAt, now, null);
    }

    public SkillDatasetImportJob failed(String message, Instant now) {
        return copy(SkillDatasetImportJobStatus.FAILED, startedAt, now, message);
    }

    private SkillDatasetImportJob copy(
            SkillDatasetImportJobStatus status,
            Instant startedAt,
            Instant completedAt,
            String errorMessage) {
        return new SkillDatasetImportJob(
                jobId, provider, datasetId, datasetName, version, language, sourceLocation,
                status, totalRows, processedRows, createdConcepts, createdRelations, failedRows,
                errorMessage, createdAt, startedAt, completedAt);
    }

    public SkillDatasetImportJob progress(

            long totalRows,

            long processedRows,

            long createdConcepts,

            long createdRelations,

            long failedRows

    ) {

        return new SkillDatasetImportJob(

                jobId,

                provider,

                datasetId,

                datasetName,

                version,

                language,

                sourceLocation,

                status,

                totalRows,

                processedRows,

                createdConcepts,

                createdRelations,

                failedRows,

                errorMessage,

                createdAt,

                startedAt,

                completedAt

        );

    }
}