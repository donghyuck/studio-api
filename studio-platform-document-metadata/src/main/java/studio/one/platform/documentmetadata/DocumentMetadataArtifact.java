package studio.one.platform.documentmetadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DocumentMetadataArtifact(
        String artifactId,
        String revisionId,
        String schemaVersion,
        String extractorVersion,
        String fingerprint,
        DocumentMetadataClassification classification,
        DocumentMetadataQuality quality,
        Map<String, DocumentMetadataField> fields,
        List<String> warnings) {

    public DocumentMetadataArtifact {
        artifactId = requireText(artifactId, "artifactId");
        revisionId = requireText(revisionId, "revisionId");
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        extractorVersion = requireText(extractorVersion, "extractorVersion");
        fingerprint = requireText(fingerprint, "fingerprint");
        if (classification == null) {
            throw new IllegalArgumentException("classification is required");
        }
        quality = quality == null ? DocumentMetadataQuality.PARTIAL : quality;
        fields = immutableFields(fields);
        warnings = warnings == null ? List.of() : warnings.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    public DocumentMetadataField field(String fieldId) {
        return fields.get(fieldId);
    }

    private static Map<String, DocumentMetadataField> immutableFields(Map<String, DocumentMetadataField> fields) {
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        Map<String, DocumentMetadataField> copy = new LinkedHashMap<>();
        fields.forEach((key, value) -> {
            if (key == null || value == null || !key.equals(value.fieldId())) {
                throw new IllegalArgumentException("field map key must match fieldId");
            }
            copy.put(key, value);
        });
        return Map.copyOf(copy);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
