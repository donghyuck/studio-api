package studio.one.platform.skillgraph.application.result;

import java.util.List;

public record SkillCategoryParentSuggestionView(
        String suggestionId,
        String suggestedName,
        List<String> childCategoryIds,
        int relationCount,
        double score,
        double confidence,
        String reason) {
}
