package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.port.SkillProjectionStore;

public class InMemorySkillProjectionStore implements SkillProjectionStore {

    private final Map<String, Map<String, SkillProjection>> projectionsByProjectionId = new ConcurrentHashMap<>();
    private final Map<String, List<SkillCluster>> clustersByProjectionId = new ConcurrentHashMap<>();

    @Override
    public void replaceProjection(String projectionId, List<SkillProjection> projections, List<SkillCluster> clusters) {
        Map<String, SkillProjection> points = new ConcurrentHashMap<>();
        for (SkillProjection projection : projections) {
            points.put(projection.skillId(), projection);
        }
        projectionsByProjectionId.put(projectionId, points);
        clustersByProjectionId.put(projectionId, clusters == null ? List.of() : List.copyOf(clusters));
    }

    @Override
    public List<SkillProjection> findProjectionPoints(String projectionId, String clusterId, int limit, int offset) {
        int max = limit <= 0 ? 100 : limit;
        int start = Math.max(0, offset);
        return projectionsByProjectionId.getOrDefault(projectionId, Map.of()).values().stream()
                .filter(point -> clusterId == null || clusterId.isBlank() || clusterId.equals(point.clusterId()))
                .sorted(Comparator.comparingInt(SkillProjection::displayOrder))
                .skip(start)
                .limit(max)
                .toList();
    }

    @Override
    public List<SkillCluster> findClusters(String projectionId) {
        return clustersByProjectionId.getOrDefault(projectionId, List.of());
    }

    @Override
    public Optional<SkillProjection> findProjectionPoint(String projectionId, String skillId) {
        return Optional.ofNullable(projectionsByProjectionId.getOrDefault(projectionId, Map.of()).get(skillId));
    }
}
