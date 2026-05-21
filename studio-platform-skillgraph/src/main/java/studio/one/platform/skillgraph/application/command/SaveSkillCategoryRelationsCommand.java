package studio.one.platform.skillgraph.application.command;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import studio.one.platform.skillgraph.domain.model.SkillCategoryRelationType;

public record SaveSkillCategoryRelationsCommand(
        @NotEmpty List<SaveSkillCategoryRelationItem> relations) {

    public record SaveSkillCategoryRelationItem(
            @Size(max = 100) String relationId,
            @Size(max = 100) String sourceCategoryId,
            @Size(max = 100) String targetCategoryId,
            SkillCategoryRelationType relationType,
            Double score,
            Double confidence,
            @Size(max = 1000) String reason) {
    }
}
