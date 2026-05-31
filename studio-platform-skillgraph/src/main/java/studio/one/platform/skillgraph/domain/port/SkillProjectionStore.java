package studio.one.platform.skillgraph.domain.port;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.model.SkillProjectionMetadata;
import studio.one.platform.skillgraph.domain.model.SkillProjectionSummary;

public interface SkillProjectionStore {

    String SERVICE_NAME = "skillProjectionStore";

    void replaceProjection(String projectionId, List<SkillProjection> projections, List<SkillCluster> clusters);

    default void replaceProjection(
            String projectionId,
            List<SkillProjection> projections,
            List<SkillCluster> clusters,
            SkillProjectionMetadata metadata) {
        replaceProjection(projectionId, projections, clusters);
    }

    Page<SkillProjectionSummary> listProjections(Pageable pageable);

    Page<SkillProjection> findProjectionPoints(String projectionId, String clusterId, Pageable pageable);

    @Deprecated(forRemoval = true)
    default List<SkillProjectionSummary> listProjections(int limit, int offset) {
        int size = limit <= 0 ? 100 : limit;
        int page = Math.max(0, offset) / size;
        return listProjections(Pageable.ofSize(size).withPage(page)).getContent();
    }

    @Deprecated(forRemoval = true)
    default List<SkillProjection> findProjectionPoints(String projectionId, String clusterId, int limit, int offset) {
        int size = limit <= 0 ? 100 : limit;
        int page = Math.max(0, offset) / size;
        return findProjectionPoints(projectionId, clusterId, Pageable.ofSize(size).withPage(page)).getContent();
    }

    List<SkillCluster> findClusters(String projectionId);

    Optional<SkillProjection> findProjectionPoint(String projectionId, String skillId);
}
