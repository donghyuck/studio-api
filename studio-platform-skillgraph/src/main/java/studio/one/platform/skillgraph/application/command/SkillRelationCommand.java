package studio.one.platform.skillgraph.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import studio.one.platform.skillgraph.domain.model.SkillRelationType;

public record SkillRelationCommand(
        @Size(max = 100) String relationId,
        @NotBlank @Size(max = 100) String sourceSkillId,
        @NotBlank @Size(max = 100) String targetSkillId,
        @NotNull SkillRelationType type) {
}
