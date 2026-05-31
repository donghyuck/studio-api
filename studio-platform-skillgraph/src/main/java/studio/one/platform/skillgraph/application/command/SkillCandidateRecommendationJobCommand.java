package studio.one.platform.skillgraph.application.command;

import java.util.List;

public record SkillCandidateRecommendationJobCommand(
        String targetScope,
        List<String> candidateIds,
        String status,
        String keyword,
        String sourceType,
        String sourceId,
        String embeddingProvider,
        String embeddingModel,
        int embeddingDimension,
        List<String> targetTypes,
        int topK,
        double minScore,
        double newSkillMinConfidence,
        double existingSkillMinScore) {
}
