package studio.one.platform.documentmetadata;

import java.util.List;

public record DocumentMetadataField(
        String fieldId,
        List<String> rawValues,
        List<String> normalizedValues,
        double confidence,
        DocumentMetadataProvenance provenance,
        List<DocumentMetadataEvidence> evidence) {

    public static final int MAX_EVIDENCE_COUNT = 3;

    public DocumentMetadataField {
        fieldId = requireText(fieldId, "fieldId");
        rawValues = normalizedList(rawValues);
        normalizedValues = normalizedList(normalizedValues);
        if (normalizedValues.isEmpty()) {
            throw new IllegalArgumentException("normalizedValues must not be empty");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        provenance = provenance == null ? DocumentMetadataProvenance.INFERRED : provenance;
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (evidence.size() > MAX_EVIDENCE_COUNT) {
            throw new IllegalArgumentException("evidence exceeds " + MAX_EVIDENCE_COUNT + " items");
        }
    }

    public boolean sourceVerified() {
        return provenance == DocumentMetadataProvenance.USER_PROVIDED
                || provenance == DocumentMetadataProvenance.NATIVE_STRUCTURED
                || provenance == DocumentMetadataProvenance.STRUCTURAL_HEURISTIC
                || provenance == DocumentMetadataProvenance.SOURCE_VERIFIED;
    }

    private static List<String> normalizedList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
