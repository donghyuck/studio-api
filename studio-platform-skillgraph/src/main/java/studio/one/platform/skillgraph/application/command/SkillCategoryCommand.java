package studio.one.platform.skillgraph.application.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillCategoryCommand(
        @Size(max = 100) String categoryId,
        @Size(max = 100) String parentCategoryId,
        @NotBlank @Size(max = 200) String name,
        @Min(0) @Max(100000) int displayOrder) {
}
