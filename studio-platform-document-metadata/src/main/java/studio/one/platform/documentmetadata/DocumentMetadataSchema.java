package studio.one.platform.documentmetadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DocumentMetadataSchema(
        DocumentSemanticType semanticType,
        String displayName,
        String description,
        List<DocumentMetadataFieldDescriptor> fields) {

    public DocumentMetadataSchema {
        if (semanticType == null) {
            throw new IllegalArgumentException("semanticType is required");
        }
        fields = fields == null ? List.of() : List.copyOf(fields);
        Map<String, DocumentMetadataFieldDescriptor> unique = new LinkedHashMap<>();
        fields.forEach(field -> {
            if (unique.put(field.fieldId(), field) != null) {
                throw new IllegalArgumentException("duplicate field: " + field.fieldId());
            }
        });
    }

    public boolean allows(String fieldId) {
        return fields.stream().anyMatch(field -> field.fieldId().equals(fieldId));
    }
}
