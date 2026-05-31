package studio.one.platform.skillgraph.application.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.result.SkillGraphBatchJobView;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobStatus;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobType;

public interface SkillGraphBatchJobService {

    String SERVICE_NAME = "features:skillgraph:batch-job-service";

    SkillGraphBatchJobView get(String jobId);

    Page<SkillGraphBatchJobView> search(SkillGraphBatchJobType jobType, SkillGraphBatchJobStatus status, Pageable pageable);
}
