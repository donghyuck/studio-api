package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.SkillDictionary;

public record SkillDictionaryView(
        String skillId,
        String name,
        String normalizedName,
        String categoryId,
        String categoryName,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static SkillDictionaryView from(SkillDictionary skill) {
        return from(skill, null);
    }

    public static SkillDictionaryView from(SkillDictionary skill, String categoryName) {
        return new SkillDictionaryView(skill.skillId(), skill.name(), skill.normalizedName(),
                skill.categoryId(), categoryName, skill.status(), skill.createdAt(), skill.updatedAt());
    }
}
