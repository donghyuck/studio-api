package studio.one.platform.skillgraph.infrastructure.skilldataset;

public interface SkillDatasetImporter {

    String provider();

    void importDataset(
            ImportCommand command,
            SkillDatasetImportProgressListener listener
    );

    record ImportCommand(
            String datasetId,
            String datasetName,
            String version,
            String language,
            String sourceLocation
    ) {
    }
}