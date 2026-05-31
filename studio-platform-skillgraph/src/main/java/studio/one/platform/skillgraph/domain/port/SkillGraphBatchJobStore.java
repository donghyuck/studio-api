package studio.one.platform.skillgraph.domain.port;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJob;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobStatus;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobType;

public interface SkillGraphBatchJobStore {

    String SERVICE_NAME = "skillGraphBatchJobStore";

    SkillGraphBatchJob save(SkillGraphBatchJob job);

    Optional<SkillGraphBatchJob> findById(String jobId);

    Page<SkillGraphBatchJob> search(SkillGraphBatchJobType jobType, SkillGraphBatchJobStatus status, Pageable pageable);
}
