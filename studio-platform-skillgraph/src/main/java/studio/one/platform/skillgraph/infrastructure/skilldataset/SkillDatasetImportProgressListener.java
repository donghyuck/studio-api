package studio.one.platform.skillgraph.infrastructure.skilldataset;

public interface SkillDatasetImportProgressListener {

    SkillDatasetImportProgressListener NOOP = (totalRows, processedRows, createdConcepts, createdRelations, failedRows) -> {
    };

    void onProgress(
            long totalRows,
            long processedRows,
            long createdConcepts,
            long createdRelations,
            long failedRows
    );
}