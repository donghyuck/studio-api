package studio.one.platform.markdown.web;

public record MarkdownRagReindexRequest(
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        boolean runSkillExtraction) {
}
