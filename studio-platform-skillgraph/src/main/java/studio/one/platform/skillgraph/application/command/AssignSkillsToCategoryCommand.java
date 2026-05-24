package studio.one.platform.skillgraph.application.command;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record AssignSkillsToCategoryCommand(
        @NotEmpty List<@Size(max = 100) String> skillIds) {
}
