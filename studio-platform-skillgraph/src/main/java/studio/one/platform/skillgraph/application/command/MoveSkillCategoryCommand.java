package studio.one.platform.skillgraph.application.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record MoveSkillCategoryCommand(
        @Size(max = 100) String parentCategoryId,
        @Min(0) @Max(100000) int displayOrder) {
}
