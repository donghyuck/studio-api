package studio.one.platform.skillgraph.application.result;

import java.time.Instant;
import java.util.List;

import studio.one.platform.skillgraph.domain.model.SkillCluster;

public record SkillClusterView(
        String clusterId,
        String label,
        String algorithm,
        int itemCount,
        String skillType,
        String jobId,
        Integer clusterLabel,
        List<String> representativeSkillIds,
        String centroidProjectionId,
        Double confidence,
        String metadata,
        Instant createdAt) {

    public static SkillClusterView from(SkillCluster cluster) {
        return new SkillClusterView(cluster.clusterId(), cluster.label(), cluster.algorithm(),
                cluster.itemCount(), cluster.skillType(), cluster.jobId(), cluster.clusterLabel(),
                cluster.representativeSkillIds(), cluster.centroidProjectionId(), cluster.confidence(), cluster.metadata(),
                cluster.createdAt());
    }
}
