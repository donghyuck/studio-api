package studio.one.platform.documentmetadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DocumentMetadataProjectionPolicy {

    static final int MAX_SUMMARY_CHARS = 480;
    static final int MAX_KEYWORDS = 8;
    static final int MAX_KEYWORD_CHARS = 80;

    public static final Set<String> VECTOR_FIELDS = Set.of(
            "title", "authors", "creators", "publicationDate", "publishedDate",
            "organization", "organizations", "institution",
            "keywords", "summary", "abstract");

    public Map<String, Object> compact(DocumentMetadataArtifact artifact) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("docMetadataId", artifact.artifactId());
        result.put("docSemanticType", artifact.classification().effectiveSemanticType().name());
        putFirst(result, "docTitle", artifact.field("title"));
        putLimited(result, "docAuthors", first(artifact.field("authors"), artifact.field("creators")), 5);
        putPublicationYear(result,
                first(artifact.field("publicationDate"), artifact.field("publishedDate")));
        putFirst(result, "docOrganization",
                first(artifact.field("organization"), artifact.field("organizations"),
                        artifact.field("institution")));
        putSummary(result, first(artifact.field("summary"), artifact.field("abstract")));
        putKeywords(result, artifact.field("keywords"));
        return Map.copyOf(result);
    }

    public Map<String, DocumentMetadataField> promptFacts(DocumentMetadataArtifact artifact) {
        Map<String, DocumentMetadataField> result = new LinkedHashMap<>();
        artifact.fields().forEach((key, value) -> {
            if (value.sourceVerified()) {
                result.put(key, value);
            }
        });
        return Map.copyOf(result);
    }

    private static DocumentMetadataField first(DocumentMetadataField... fields) {
        for (DocumentMetadataField field : fields) {
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    private static void putFirst(Map<String, Object> target, String key, DocumentMetadataField field) {
        if (field != null && !field.normalizedValues().isEmpty()) {
            target.put(key, field.normalizedValues().get(0));
        }
    }

    private static void putLimited(Map<String, Object> target, String key, DocumentMetadataField field, int limit) {
        if (field != null && !field.normalizedValues().isEmpty()) {
            List<String> values = field.normalizedValues();
            target.put(key, values.subList(0, Math.min(limit, values.size())));
        }
    }

    private static void putPublicationYear(Map<String, Object> target, DocumentMetadataField field) {
        if (field == null || field.normalizedValues().isEmpty()) {
            return;
        }
        String value = field.normalizedValues().get(0);
        if (value.matches("\\d{4}(?:-\\d{2}(?:-\\d{2})?)?")) {
            target.put("docPublicationYear", value.substring(0, 4));
        }
    }

    private static void putSummary(Map<String, Object> target, DocumentMetadataField field) {
        if (field == null || field.normalizedValues().isEmpty()) {
            return;
        }
        String summary = field.normalizedValues().get(0);
        if (summary == null || summary.isBlank()) {
            return;
        }
        String normalized = truncateCodePoints(normalize(summary), MAX_SUMMARY_CHARS);
        if (!normalized.isEmpty()) {
            target.put("docSummary", normalized);
        }
    }

    private static void putKeywords(Map<String, Object> target, DocumentMetadataField field) {
        if (field == null || field.normalizedValues().isEmpty()) {
            return;
        }
        List<String> keywords = field.normalizedValues().stream()
                .map(DocumentMetadataProjectionPolicy::normalize)
                .filter(value -> !value.isEmpty())
                .map(value -> truncateCodePoints(value, MAX_KEYWORD_CHARS))
                .distinct()
                .limit(MAX_KEYWORDS)
                .toList();
        if (!keywords.isEmpty()) {
            target.put("docKeywords", keywords);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String truncateCodePoints(String value, int maxCodePoints) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        int endIndex = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, endIndex).trim();
    }
}
