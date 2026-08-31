package studio.one.platform.markdown.application;

import java.util.List;

import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.documentmetadata.DocumentMetadataProjectionPolicy;
import studio.one.platform.documentmetadata.DocumentMetadataQuality;

public record DocumentMetadataSummaryView(
        String documentId,
        String revisionId,
        String artifactId,
        String semanticType,
        String subject,
        Double confidence,
        String language,
        String title,
        List<String> authors,
        String publicationYear,
        String organization,
        String summary,
        List<String> keywords,
        DocumentMetadataQuality quality,
        List<String> warnings) {

    public DocumentMetadataSummaryView {
        authors = authors == null ? List.of() : List.copyOf(authors);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static DocumentMetadataSummaryView from(
            String documentId,
            DocumentMetadataArtifact artifact,
            DocumentMetadataProjectionPolicy projectionPolicy) {
        DocumentMetadataProjectionPolicy projection = projectionPolicy == null
                ? new DocumentMetadataProjectionPolicy()
                : projectionPolicy;
        var compact = projection.compact(artifact);
        return new DocumentMetadataSummaryView(
                documentId,
                artifact.revisionId(),
                artifact.artifactId(),
                artifact.classification().effectiveSemanticType().name(),
                artifact.classification().subject(),
                artifact.classification().confidence(),
                firstValue(artifact, "language"),
                text(compact.get("docTitle")),
                strings(compact.get("docAuthors")),
                text(compact.get("docPublicationYear")),
                text(compact.get("docOrganization")),
                text(compact.get("docSummary")),
                strings(compact.get("docKeywords")),
                artifact.quality(),
                artifact.warnings());
    }

    private static String firstValue(DocumentMetadataArtifact artifact, String fieldId) {
        var field = artifact.field(fieldId);
        return field == null || field.normalizedValues().isEmpty()
                ? null
                : text(field.normalizedValues().get(0));
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<String> strings(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.ArrayList<String> result = new java.util.ArrayList<>();
            for (Object item : iterable) {
                String normalized = text(item);
                if (normalized != null) {
                    result.add(normalized);
                }
            }
            return List.copyOf(result);
        }
        String normalized = text(value);
        return normalized == null ? List.of() : List.of(normalized);
    }
}
