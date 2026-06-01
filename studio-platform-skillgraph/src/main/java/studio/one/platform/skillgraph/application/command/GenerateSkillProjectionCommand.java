package studio.one.platform.skillgraph.application.command;

import studio.one.platform.skillgraph.domain.model.SkillType;

public record GenerateSkillProjectionCommand(
        String projectionId,
        int limit,
        String skillType,
        String projectionType,
        String reductionAlgorithm,
        Integer projectionDimension,
        String clusteringAlgorithm,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        String parameters) {

    public GenerateSkillProjectionCommand(
            String projectionId,
            int limit,
            String reductionAlgorithm,
            String clusteringAlgorithm,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension) {
        this(projectionId, limit, null, null, reductionAlgorithm, null, clusteringAlgorithm, embeddingProvider,
                embeddingModel, embeddingDimension, null);
    }

    public GenerateSkillProjectionCommand {
        skillType = SkillType.normalizeName(skillType);
    }
}
