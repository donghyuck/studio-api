package studio.one.platform.markdown.web;

public record MarkdownRagReindexRequest(
        String embeddingDeploymentId,
        String embeddingModelId,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        boolean useLlmKeywordExtraction,
        boolean runSkillExtraction,
        String skillExtractionMode,
        boolean generateSkillEmbeddings,
        String skillEmbeddingProvider,
        String skillEmbeddingModel,
        Integer skillEmbeddingDimension) {
    public MarkdownRagReindexRequest(
            String embeddingProfileId, String embeddingProvider, String embeddingModel,
            Integer embeddingDimension, boolean useLlmKeywordExtraction, boolean runSkillExtraction,
            String skillExtractionMode, boolean generateSkillEmbeddings,
            String skillEmbeddingProvider, String skillEmbeddingModel, Integer skillEmbeddingDimension) {
        this(null, null, embeddingProfileId, embeddingProvider, embeddingModel, embeddingDimension,
                useLlmKeywordExtraction, runSkillExtraction, skillExtractionMode, generateSkillEmbeddings,
                skillEmbeddingProvider, skillEmbeddingModel, skillEmbeddingDimension);
    }
}
