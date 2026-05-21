package studio.one.platform.skillgraph.application.command;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record MergeSkillCategoriesCommand(
        @NotEmpty List<@Size(max = 100) String> sourceCategoryIds,
        @NotBlank @Size(max = 100) String targetCategoryId,
        Boolean deleteSources) {
}
