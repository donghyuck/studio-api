package studio.one.platform.skillgraph.application.command;

import studio.one.platform.skillgraph.domain.model.SkillRelationType;

public record SkillRelationCommand(String relationId, String sourceSkillId, String targetSkillId, SkillRelationType type) {
}
