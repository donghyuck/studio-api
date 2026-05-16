package studio.one.platform.skillgraph.application.result;

import java.util.List;

public record SkillProjectionResult(
        String projectionId,
        int itemCount,
        int clusterCount,
        List<SkillProjectionPointView> points,
        List<SkillClusterView> clusters) {
}
