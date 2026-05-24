package studio.one.platform.skillgraph.application.command;

import java.util.List;

public record GenerateSkillCategoryDraftCommand(
        String projectionId,
        List<String> clusterIds,
        Integer representativeLimit,
        Boolean includeNoise,
        Boolean useLlm) {
}
