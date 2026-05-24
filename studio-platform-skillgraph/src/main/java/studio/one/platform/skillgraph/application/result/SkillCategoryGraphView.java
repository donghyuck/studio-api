package studio.one.platform.skillgraph.application.result;

import java.util.List;

public record SkillCategoryGraphView(
        List<SkillCategoryView> categories,
        List<SkillDictionaryView> skills,
        List<SkillCategoryRelationView> relations,
        List<SkillCategoryParentSuggestionView> parentSuggestions) {
}
