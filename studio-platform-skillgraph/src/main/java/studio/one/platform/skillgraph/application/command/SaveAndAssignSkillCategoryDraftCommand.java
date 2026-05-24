package studio.one.platform.skillgraph.application.command;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record SaveAndAssignSkillCategoryDraftCommand(
        @NotBlank @Size(max = 100) String projectionId,
        Boolean includeNoise,
        @NotEmpty List<SaveAndAssignSkillCategoryDraftItem> drafts) {

    public record SaveAndAssignSkillCategoryDraftItem(
            @NotBlank @Size(max = 100) String clusterId,
            @Size(max = 100) String categoryId,
            @Size(max = 100) String parentCategoryId,
            @NotBlank @Size(max = 200) String name,
            Integer displayOrder) {
    }
}
