package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.SkillCategoryRelation;
import studio.one.platform.skillgraph.domain.model.SkillCategoryRelationType;

public record SkillCategoryRelationView(
        String relationId,
        String sourceCategoryId,
        String targetCategoryId,
        SkillCategoryRelationType relationType,
        double score,
        double confidence,
        String reason,
        boolean persisted,
        Instant createdAt,
        Instant updatedAt) {

    public static SkillCategoryRelationView from(SkillCategoryRelation relation) {
        return new SkillCategoryRelationView(
                relation.relationId(),
                relation.sourceCategoryId(),
                relation.targetCategoryId(),
                relation.relationType(),
                relation.score(),
                relation.confidence(),
                relation.reason(),
                true,
                relation.createdAt(),
                relation.updatedAt());
    }

    public static SkillCategoryRelationView preview(
            String sourceCategoryId,
            String targetCategoryId,
            SkillCategoryRelationType relationType,
            double score,
            double confidence,
            String reason) {
        return new SkillCategoryRelationView(
                null,
                sourceCategoryId,
                targetCategoryId,
                relationType,
                score,
                confidence,
                reason,
                false,
                null,
                null);
    }
}
