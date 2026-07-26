package studio.one.platform.documentmetadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DocumentMetadataProjectionPolicy {

    public static final Set<String> VECTOR_FIELDS = Set.of(
            "title", "authors", "creators", "publicationDate", "publishedDate",
            "organization", "organizations", "institution");

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
}
