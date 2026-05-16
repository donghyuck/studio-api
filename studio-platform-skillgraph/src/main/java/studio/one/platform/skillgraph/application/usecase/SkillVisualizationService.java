package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import studio.one.platform.skillgraph.application.result.SkillClusterView;
import studio.one.platform.skillgraph.application.result.SkillProjectionPointView;
import studio.one.platform.skillgraph.application.result.SkillProjectionResult;

public interface SkillVisualizationService {

    String SERVICE_NAME = "skillVisualizationService";

    SkillProjectionResult generateProjection(String projectionId, int limit);

    List<SkillProjectionPointView> findProjectionPoints(String projectionId, String clusterId, int limit, int offset);

    List<SkillClusterView> findClusters(String projectionId);
}
