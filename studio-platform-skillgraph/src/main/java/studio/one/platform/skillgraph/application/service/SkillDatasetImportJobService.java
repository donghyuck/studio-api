package studio.one.platform.skillgraph.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.SkillDatasetImportCommand;
import studio.one.platform.skillgraph.domain.model.SkillDatasetImportJob;
import studio.one.platform.skillgraph.domain.model.SkillDatasetImportJobStatus;
import studio.one.platform.skillgraph.domain.port.SkillDatasetImportJobStore;

@RequiredArgsConstructor
public class SkillDatasetImportJobService {

    private final SkillDatasetImportJobStore jobStore;
    private final SkillDatasetImportJobWorker worker;
    private final Executor executor;

    public SkillDatasetImportJob create(SkillDatasetImportCommand command) {
        validate(command);
        String datasetName = isBlank(command.datasetName()) ? command.datasetId() : command.datasetName();
        SkillDatasetImportJob job = new SkillDatasetImportJob(
                "sdij_" + UUID.randomUUID(),
                command.provider(),
                command.datasetId(),
                datasetName,
                command.version(),
                command.language(),
                command.sourceLocation(),
                SkillDatasetImportJobStatus.QUEUED,
                0, 0, 0, 0, 0,
                null,
                Instant.now(),
                null,
                null
        );
        SkillDatasetImportJob saved = jobStore.save(job);
        executor.execute(() -> worker.run(saved.jobId()));
        return saved;
    }

    public SkillDatasetImportJob get(String jobId) {
        return jobStore.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown import job: " + jobId));
    }

    public List<SkillDatasetImportJob> recent(int limit) {
        return jobStore.findRecent(limit <= 0 ? 20 : Math.min(limit, 100));
    }

    private void validate(SkillDatasetImportCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (isBlank(command.provider())) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (isBlank(command.datasetId())) {
            throw new IllegalArgumentException("datasetId must not be blank");
        }
        if (isBlank(command.sourceLocation())) {
            throw new IllegalArgumentException("sourceLocation must not be blank");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
