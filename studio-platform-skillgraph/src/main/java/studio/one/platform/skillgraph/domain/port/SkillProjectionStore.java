package studio.one.platform.skillgraph.domain.port;

import java.util.List;
import java.util.Optional;

import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.model.SkillProjectionSummary;

public interface SkillProjectionStore {

    String SERVICE_NAME = "skillProjectionStore";

    void replaceProjection(String projectionId, List<SkillProjection> projections, List<SkillCluster> clusters);

    List<SkillProjectionSummary> listProjections(int limit, int offset);

    List<SkillProjection> findProjectionPoints(String projectionId, String clusterId, int limit, int offset);

    List<SkillCluster> findClusters(String projectionId);

    Optional<SkillProjection> findProjectionPoint(String projectionId, String skillId);
}
