package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.model.SkillProjectionSummary;
import studio.one.platform.skillgraph.domain.port.SkillProjectionStore;

public class InMemorySkillProjectionStore implements SkillProjectionStore {

    private final Map<String, Map<String, SkillProjection>> projectionsByProjectionId = new ConcurrentHashMap<>();
    private final Map<String, List<SkillCluster>> clustersByProjectionId = new ConcurrentHashMap<>();

    @Override
    public void replaceProjection(String projectionId, List<SkillProjection> projections, List<SkillCluster> clusters) {
        if (projections == null || projections.isEmpty()) {
            projectionsByProjectionId.remove(projectionId);
            clustersByProjectionId.remove(projectionId);
            return;
        }
        Map<String, SkillProjection> points = new ConcurrentHashMap<>();
        for (SkillProjection projection : projections) {
            points.put(projection.skillId(), projection);
        }
        projectionsByProjectionId.put(projectionId, points);
        clustersByProjectionId.put(projectionId, clusters == null ? List.of() : List.copyOf(clusters));
    }

    @Override
    public List<SkillProjectionSummary> listProjections(int limit, int offset) {
        int max = limit <= 0 ? 100 : limit;
        int start = Math.max(0, offset);
        return projectionsByProjectionId.entrySet().stream()
                .map(entry -> summary(entry.getKey(), entry.getValue().values().stream().toList()))
                .sorted(Comparator.comparing(SkillProjectionSummary::updatedAt).reversed()
                        .thenComparing(SkillProjectionSummary::projectionId))
                .skip(start)
                .limit(max)
                .toList();
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

    private SkillProjectionSummary summary(String projectionId, List<SkillProjection> projections) {
        List<SkillCluster> clusters = clustersByProjectionId.getOrDefault(projectionId, List.of());
        return new SkillProjectionSummary(
                projectionId,
                projections.size(),
                (int) projections.stream()
                        .map(SkillProjection::clusterId)
                        .filter(clusterId -> clusterId != null && !clusterId.isBlank())
                        .distinct()
                        .count(),
                clusters.stream()
                        .map(SkillCluster::algorithm)
                        .filter(algorithm -> algorithm != null && !algorithm.isBlank())
                        .findFirst()
                        .orElse(null),
                projections.stream()
                        .map(SkillProjection::createdAt)
                        .min(Comparator.naturalOrder())
                        .orElse(null),
                projections.stream()
                        .map(SkillProjection::createdAt)
                        .max(Comparator.naturalOrder())
                        .orElse(null));
    }
}
