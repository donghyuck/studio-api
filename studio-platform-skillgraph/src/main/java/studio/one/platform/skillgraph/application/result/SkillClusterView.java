package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.SkillCluster;

public record SkillClusterView(
        String clusterId,
        String label,
        String algorithm,
        int itemCount,
        Instant createdAt) {

    public static SkillClusterView from(SkillCluster cluster) {
        return new SkillClusterView(cluster.clusterId(), cluster.label(), cluster.algorithm(),
                cluster.itemCount(), cluster.createdAt());
    }
}
