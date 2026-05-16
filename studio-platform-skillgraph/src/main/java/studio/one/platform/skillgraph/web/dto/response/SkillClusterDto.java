package studio.one.platform.skillgraph.web.dto.response;

import java.time.Instant;

import studio.one.platform.skillgraph.application.result.SkillClusterView;

public record SkillClusterDto(String clusterId, String label, String algorithm, int itemCount, Instant createdAt) {

    public static SkillClusterDto from(SkillClusterView view) {
        return new SkillClusterDto(view.clusterId(), view.label(), view.algorithm(), view.itemCount(), view.createdAt());
    }
}
