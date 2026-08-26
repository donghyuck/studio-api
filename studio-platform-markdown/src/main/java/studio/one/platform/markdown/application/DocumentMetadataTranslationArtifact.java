package studio.one.platform.markdown.application;

import java.time.Instant;
import java.util.List;

public record DocumentMetadataTranslationArtifact(
        String translationId,
        String revisionId,
        String sourceArtifactId,
        String sourceSummaryHash,
        String sourceLanguage,
        String targetLanguage,
        String summary,
        List<String> keywords,
        GenerationMode generationMode,
        String model,
        String promptVersion,
        Instant createdAt) {

    public DocumentMetadataTranslationArtifact {
        translationId = requireText(translationId, "translationId");
        revisionId = requireText(revisionId, "revisionId");
        sourceArtifactId = requireText(sourceArtifactId, "sourceArtifactId");
        sourceSummaryHash = requireText(sourceSummaryHash, "sourceSummaryHash");
        sourceLanguage = normalizeLanguage(sourceLanguage);
        targetLanguage = normalizeLanguage(targetLanguage);
        summary = requireText(summary, "summary");
        keywords = keywords == null ? List.of() : keywords.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .limit(8)
                .toList();
        generationMode = generationMode == null ? GenerationMode.TRANSLATED : generationMode;
        promptVersion = requireText(promptVersion, "promptVersion");
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public enum GenerationMode {
        TRANSLATED,
        SOURCE_REUSED
    }

    private static String normalizeLanguage(String value) {
        return requireText(value, "language").toLowerCase(java.util.Locale.ROOT);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
