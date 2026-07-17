package studio.one.platform.markdown.application;

import java.util.List;

public record MarkdownDocumentProfileDescriptor(
        String id,
        String displayName,
        String description,
        String version,
        String costTier,
        List<String> supportedFormats,
        String chunkingStrategy,
        int chunkMaxSize,
        int chunkOverlap,
        String chunkUnit,
        boolean ocrRequired,
        String ocrLanguage,
        String ocrMode,
        boolean mathVisionCorrection) {
}
