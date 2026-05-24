package studio.one.platform.skillgraph.application.service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.skillgraph.domain.model.SkillDatasetImportJob;
import studio.one.platform.skillgraph.domain.port.SkillDatasetImportJobStore;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDatasetImporter;

@Slf4j
@RequiredArgsConstructor
public class SkillDatasetImportJobWorker {

    private final SkillDatasetImportJobStore jobStore;
    private final List<SkillDatasetImporter> importers;

    public void run(String jobId) {
        SkillDatasetImportJob job = jobStore.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown import job: " + jobId));

        AtomicReference<SkillDatasetImportJob> current = new AtomicReference<>(
                jobStore.save(job.running(Instant.now()))
        );

        try {
            SkillDatasetImporter importer = findImporter(job.provider());

            importer.importDataset(
                    new SkillDatasetImporter.ImportCommand(
                            job.datasetId(),
                            job.datasetName(),
                            job.version(),
                            job.language(),
                            job.sourceLocation()
                    ),
                    (totalRows, processedRows, createdConcepts, createdRelations, failedRows) -> {
                        SkillDatasetImportJob updated = current.get().progress(
                                totalRows,
                                processedRows,
                                createdConcepts,
                                createdRelations,
                                failedRows
                        );
                        current.set(jobStore.save(updated));
                    }
            );

            jobStore.save(current.get().completed(Instant.now()));

        } catch (Exception ex) {
            log.error("Failed to import skill dataset. jobId={}", jobId, ex);
            jobStore.save(current.get().failed(rootCauseMessage(ex), Instant.now()));
        }
    }

    private SkillDatasetImporter findImporter(String provider) {
        return importers.stream()
                .filter(importer -> importer.provider().equalsIgnoreCase(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported dataset provider: " + provider));
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getMessage();
        }
        return message;
    }
}
