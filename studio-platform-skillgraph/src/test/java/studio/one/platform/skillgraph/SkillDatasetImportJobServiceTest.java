package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import studio.one.platform.skillgraph.application.command.SkillDatasetImportCommand;
import studio.one.platform.skillgraph.application.service.SkillDatasetImportJobService;
import studio.one.platform.skillgraph.application.service.SkillDatasetImportJobWorker;
import studio.one.platform.skillgraph.domain.model.SkillDatasetImportJob;
import studio.one.platform.skillgraph.domain.port.SkillDatasetImportJobStore;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDatasetImportProgressListener;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDatasetImporter;

class SkillDatasetImportJobServiceTest {

    @Test
    void usesDatasetIdAsDatasetNameWhenNameIsBlank() {
        Store store = new Store();
        SkillDatasetImportJobService service = new SkillDatasetImportJobService(
                store,
                new SkillDatasetImportJobWorker(store, List.of(new NoopImporter())),
                Runnable::run);

        SkillDatasetImportJob job = service.create(new SkillDatasetImportCommand(
                "ncs",
                "ncs-2026",
                null,
                "2026",
                "ko",
                "/tmp/ncs.xlsx"));

        assertEquals("ncs-2026", job.datasetName());
        assertEquals("ncs-2026", service.get(job.jobId()).datasetName());
    }

    private static final class Store implements SkillDatasetImportJobStore {

        private final Map<String, SkillDatasetImportJob> jobs = new ConcurrentHashMap<>();

        @Override
        public SkillDatasetImportJob save(SkillDatasetImportJob job) {
            jobs.put(job.jobId(), job);
            return job;
        }

        @Override
        public Optional<SkillDatasetImportJob> findById(String jobId) {
            return Optional.ofNullable(jobs.get(jobId));
        }

        @Override
        public List<SkillDatasetImportJob> findRecent(int limit) {
            return List.copyOf(jobs.values());
        }
    }

    private static final class NoopImporter implements SkillDatasetImporter {

        @Override
        public String provider() {
            return "ncs";
        }

        @Override
        public void importDataset(ImportCommand command, SkillDatasetImportProgressListener listener) {
            if (listener != null) {
                listener.onProgress(0, 0, 0, 0, 0);
            }
        }
    }
}
