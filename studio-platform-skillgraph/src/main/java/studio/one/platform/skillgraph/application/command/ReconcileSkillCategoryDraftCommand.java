package studio.one.platform.skillgraph.application.command;

public record ReconcileSkillCategoryDraftCommand(
        Integer limit,
        Double existingCategoryMinSimilarity,
        Double newCategoryMinSimilarity,
        Integer minClusterSize,
        Integer representativeLimit,
        Boolean useLlm) {
}
