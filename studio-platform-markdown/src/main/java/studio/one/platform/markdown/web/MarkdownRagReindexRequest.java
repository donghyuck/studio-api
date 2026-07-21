package studio.one.platform.markdown.web;

public record MarkdownRagReindexRequest(
        @com.fasterxml.jackson.annotation.JsonAlias("embeddingModelId") String embeddingProfileId,
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
}
