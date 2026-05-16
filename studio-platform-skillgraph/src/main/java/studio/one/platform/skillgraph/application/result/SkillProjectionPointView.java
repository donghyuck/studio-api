package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.SkillProjection;

public record SkillProjectionPointView(
        String projectionId,
        String skillId,
        double x,
        double y,
        String clusterId,
        int displayOrder,
        Instant createdAt) {

    public static SkillProjectionPointView from(SkillProjection projection) {
        return new SkillProjectionPointView(
                projection.projectionId(),
                projection.skillId(),
                projection.x(),
                projection.y(),
                projection.clusterId(),
                projection.displayOrder(),
                projection.createdAt());
    }
}
