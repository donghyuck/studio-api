package studio.one.platform.skillgraph.application.result;

import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillDictionaryMatchType;

public record SkillMatchedDictionaryView(
        String skillId,
        String name,
        String normalizedName,
        String categoryId,
        String status,
        SkillDictionaryMatchType matchType,
        double similarityScore) {

    public static SkillMatchedDictionaryView from(
            SkillDictionary skill,
            SkillDictionaryMatchType matchType,
            double similarityScore) {
        return new SkillMatchedDictionaryView(
                skill.skillId(),
                skill.name(),
                skill.normalizedName(),
                skill.categoryId(),
                skill.status(),
                matchType,
                Math.max(0.0d, Math.min(1.0d, similarityScore)));
    }
}
