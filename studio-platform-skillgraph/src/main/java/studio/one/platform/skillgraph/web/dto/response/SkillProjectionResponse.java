package studio.one.platform.skillgraph.web.dto.response;

import java.util.List;

import studio.one.platform.skillgraph.application.result.SkillProjectionResult;

public record SkillProjectionResponse(
        String projectionId,
        int itemCount,
        int clusterCount,
        List<SkillProjectionPointDto> points,
        List<SkillClusterDto> clusters) {

    public static SkillProjectionResponse from(SkillProjectionResult result) {
        return new SkillProjectionResponse(result.projectionId(), result.itemCount(), result.clusterCount(),
                result.points().stream().map(SkillProjectionPointDto::from).toList(),
                result.clusters().stream().map(SkillClusterDto::from).toList());
    }
}
