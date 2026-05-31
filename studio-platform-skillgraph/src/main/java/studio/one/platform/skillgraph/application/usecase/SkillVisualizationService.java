package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.result.SkillClusterView;
import studio.one.platform.skillgraph.application.command.GenerateSkillProjectionCommand;
import studio.one.platform.skillgraph.application.result.SkillProjectionPointView;
import studio.one.platform.skillgraph.application.result.SkillProjectionResult;
import studio.one.platform.skillgraph.application.result.SkillProjectionSummaryView;
import studio.one.platform.skillgraph.application.result.SkillGraphBatchJobView;

public interface SkillVisualizationService {

    String SERVICE_NAME = "skillVisualizationService";

    SkillProjectionResult generateProjection(String projectionId, int limit);

    default SkillProjectionResult generateProjection(GenerateSkillProjectionCommand command) {
        return generateProjection(command.projectionId(), command.limit());
    }

    default SkillGraphBatchJobView generateProjectionJob(GenerateSkillProjectionCommand command) {
        throw new UnsupportedOperationException("Projection batch job is not implemented");
    }

    Page<SkillProjectionSummaryView> listProjections(Pageable pageable);

    Page<SkillProjectionPointView> findProjectionPoints(String projectionId, String clusterId, Pageable pageable);

    List<SkillClusterView> findClusters(String projectionId);
}
