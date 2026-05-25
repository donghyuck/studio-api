package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDataset;

public record SkillReferenceDatasetView(
        String datasetId,
        String provider,
        String datasetName,
        String version,
        String language,
        String sourceLocation,
        Instant importedAt) {

    public static SkillReferenceDatasetView from(SkillDataset dataset) {
        return new SkillReferenceDatasetView(
                dataset.datasetId(),
                dataset.provider(),
                dataset.datasetName(),
                dataset.version(),
                dataset.language(),
                dataset.sourceLocation(),
                dataset.importedAt());
    }
}
