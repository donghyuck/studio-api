package studio.one.platform.skillgraph.application.result;

import java.util.List;

public record SkillCategoryDraftView(
        String draftId,
        String clusterId,
        String proposedName,
        String suggestedCategoryName,
        double confidence,
        boolean noise,
        int itemCount,
        List<String> representativeSkillIds,
        List<String> representativeSkillNames,
        List<SkillClusterRepresentativeView> representativeSkills) {
}
