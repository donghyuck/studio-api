package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.SkillProjectionSummary;

public record SkillProjectionSummaryView(
        String projectionId,
        int itemCount,
        int clusterCount,
        String algorithm,
        Instant createdAt,
        Instant updatedAt) {

    public static SkillProjectionSummaryView from(SkillProjectionSummary summary) {
        return new SkillProjectionSummaryView(
                summary.projectionId(),
                summary.itemCount(),
                summary.clusterCount(),
                summary.algorithm(),
                summary.createdAt(),
                summary.updatedAt());
    }
}
