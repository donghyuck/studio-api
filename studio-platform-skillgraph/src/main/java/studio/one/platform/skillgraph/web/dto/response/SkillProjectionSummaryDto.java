package studio.one.platform.skillgraph.web.dto.response;

import java.time.Instant;

import studio.one.platform.skillgraph.application.result.SkillProjectionSummaryView;

public record SkillProjectionSummaryDto(
        String projectionId,
        String name,
        String status,
        String algorithm,
        int itemCount,
        int clusterCount,
        Instant createdAt,
        Instant updatedAt) {

    public static SkillProjectionSummaryDto from(SkillProjectionSummaryView view) {
        return new SkillProjectionSummaryDto(
                view.projectionId(),
                view.projectionId(),
                "READY",
                view.algorithm(),
                view.itemCount(),
                view.clusterCount(),
                view.createdAt(),
                view.updatedAt());
    }
}
