package studio.one.platform.documentmetadata;

import java.util.List;
import java.util.Optional;

public interface DocumentMetadataSchemaRegistry {

    String schemaVersion();

    List<DocumentMetadataSchema> schemas();

    default Optional<DocumentMetadataSchema> find(DocumentSemanticType type) {
        return schemas().stream().filter(schema -> schema.semanticType() == type).findFirst();
    }

    default DocumentMetadataSchema require(DocumentSemanticType type) {
        return find(type).orElseThrow(() -> new IllegalArgumentException("Unknown document semantic type: " + type));
    }
}
