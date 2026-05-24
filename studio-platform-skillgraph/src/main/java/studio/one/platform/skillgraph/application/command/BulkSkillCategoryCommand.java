package studio.one.platform.skillgraph.application.command;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record BulkSkillCategoryCommand(
        @NotBlank @Size(max = 50) String operation,
        @NotEmpty List<@Size(max = 100) String> ids,
        @Size(max = 100) String categoryId,
        @Size(max = 100) String parentCategoryId,
        @Size(max = 30) String status,
        Integer displayOrder) {
}
