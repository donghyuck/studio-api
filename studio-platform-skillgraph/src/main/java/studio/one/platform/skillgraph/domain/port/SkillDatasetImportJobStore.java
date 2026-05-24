package studio.one.platform.skillgraph.domain.port;

import java.util.List;
import java.util.Optional;

import studio.one.platform.skillgraph.domain.model.SkillDatasetImportJob;

public interface SkillDatasetImportJobStore {

    String SERVICE_NAME = "components:skill-dataset-import-jobstore";

    SkillDatasetImportJob save(SkillDatasetImportJob job);

    Optional<SkillDatasetImportJob> findById(String jobId);

    List<SkillDatasetImportJob> findRecent(int limit);

}