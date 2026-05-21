package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
    public Page<SkillProjectionSummary> listProjections(Pageable pageable) {
        List<SkillProjectionSummary> filtered = projectionsByProjectionId.entrySet().stream()
                .map(entry -> summary(entry.getKey(), entry.getValue().values().stream().toList()))
                .sorted(Comparator.comparing(SkillProjectionSummary::updatedAt).reversed()
                        .thenComparing(SkillProjectionSummary::projectionId))
                .toList();
        int start = Math.toIntExact(Math.min(pageable.getOffset(), filtered.size()));
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    @Override
    public Page<SkillProjection> findProjectionPoints(String projectionId, String clusterId, Pageable pageable) {
        List<SkillProjection> filtered = projectionsByProjectionId.getOrDefault(projectionId, Map.of()).values().stream()
                .filter(point -> clusterId == null || clusterId.isBlank() || clusterId.equals(point.clusterId()))
                .sorted(Comparator.comparingInt(SkillProjection::displayOrder))
                .toList();
        int start = Math.toIntExact(Math.min(pageable.getOffset(), filtered.size()));
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
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
