package studio.one.platform.skillgraph.web.dto.response;

import java.time.Instant;

import studio.one.platform.skillgraph.application.result.SkillProjectionPointView;

public record SkillProjectionPointDto(
        String projectionId,
        String skillId,
        double x,
        double y,
        String clusterId,
        int displayOrder,
        Instant createdAt) {

    public static SkillProjectionPointDto from(SkillProjectionPointView view) {
        return new SkillProjectionPointDto(view.projectionId(), view.skillId(), view.x(), view.y(),
                view.clusterId(), view.displayOrder(), view.createdAt());
    }
}
