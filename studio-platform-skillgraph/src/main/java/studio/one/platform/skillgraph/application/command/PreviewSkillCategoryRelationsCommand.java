package studio.one.platform.skillgraph.application.command;

import java.util.List;

import jakarta.validation.constraints.Size;

public record PreviewSkillCategoryRelationsCommand(
        List<@Size(max = 100) String> categoryIds,
        Integer representativeSkillLimit,
        Double minScore,
        Boolean includePersisted,
        Boolean useLlm) {
}
