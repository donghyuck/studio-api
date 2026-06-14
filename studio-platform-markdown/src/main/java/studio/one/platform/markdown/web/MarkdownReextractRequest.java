package studio.one.platform.markdown.web;

public record MarkdownReextractRequest(
        boolean runChunking,
        boolean runRagIndex,
        boolean runSkillExtraction,
        String chunkingStrategy,
        Integer chunkMaxSize,
        Integer chunkOverlap,
        String chunkUnit,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension) {
}
