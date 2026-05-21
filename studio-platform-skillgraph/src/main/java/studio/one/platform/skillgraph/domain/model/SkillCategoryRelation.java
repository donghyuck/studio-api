package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record SkillCategoryRelation(
        String relationId,
        String sourceCategoryId,
        String targetCategoryId,
        SkillCategoryRelationType relationType,
        double score,
        double confidence,
        String reason,
        Instant createdAt,
        Instant updatedAt) {
}
