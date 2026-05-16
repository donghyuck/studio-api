package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.SkillRelation;
import studio.one.platform.skillgraph.domain.model.SkillRelationType;

public record SkillRelationView(
        String relationId,
        String sourceSkillId,
        String targetSkillId,
        SkillRelationType type,
        Instant createdAt) {

    public static SkillRelationView from(SkillRelation relation) {
        return new SkillRelationView(relation.relationId(), relation.sourceSkillId(), relation.targetSkillId(),
                relation.type(), relation.createdAt());
    }
}
