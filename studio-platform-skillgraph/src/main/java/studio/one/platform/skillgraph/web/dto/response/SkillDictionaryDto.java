package studio.one.platform.skillgraph.web.dto.response;

import java.time.Instant;

import studio.one.platform.skillgraph.application.result.SkillDictionaryView;

public record SkillDictionaryDto(
        String skillId,
        String name,
        String normalizedName,
        String categoryId,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static SkillDictionaryDto from(SkillDictionaryView view) {
        return new SkillDictionaryDto(view.skillId(), view.name(), view.normalizedName(),
                view.categoryId(), view.status(), view.createdAt(), view.updatedAt());
    }
}
