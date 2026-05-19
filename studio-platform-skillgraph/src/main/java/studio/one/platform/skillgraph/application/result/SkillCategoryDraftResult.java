package studio.one.platform.skillgraph.application.result;

import java.util.List;

public record SkillCategoryDraftResult(
        String projectionId,
        int draftCount,
        int noiseCount,
        List<SkillCategoryDraftView> drafts) {
}
