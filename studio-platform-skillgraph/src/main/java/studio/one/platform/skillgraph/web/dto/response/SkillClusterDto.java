package studio.one.platform.skillgraph.web.dto.response;

import java.time.Instant;
import java.util.List;

import studio.one.platform.skillgraph.application.result.SkillClusterView;

public record SkillClusterDto(
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

    public static SkillClusterDto from(SkillClusterView view) {
        return new SkillClusterDto(
                view.clusterId(),
                view.label(),
                view.algorithm(),
                view.itemCount(),
                view.skillType(),
                view.jobId(),
                view.clusterLabel(),
                view.representativeSkillIds(),
                view.centroidProjectionId(),
                view.confidence(),
                view.metadata(),
                view.createdAt());
    }
}
