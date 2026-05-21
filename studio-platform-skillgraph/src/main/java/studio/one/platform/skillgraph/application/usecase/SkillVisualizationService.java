package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.result.SkillClusterView;
import studio.one.platform.skillgraph.application.result.SkillProjectionPointView;
import studio.one.platform.skillgraph.application.result.SkillProjectionResult;
import studio.one.platform.skillgraph.application.result.SkillProjectionSummaryView;

public interface SkillVisualizationService {

    String SERVICE_NAME = "skillVisualizationService";

    SkillProjectionResult generateProjection(String projectionId, int limit);

    Page<SkillProjectionSummaryView> listProjections(Pageable pageable);

    Page<SkillProjectionPointView> findProjectionPoints(String projectionId, String clusterId, Pageable pageable);

    List<SkillClusterView> findClusters(String projectionId);
}
