package studio.one.application.web.service;

import java.util.List;
import java.util.Map;

public record AttachmentRagIndexCommand(
        String documentId,
        String objectType,
        String objectId,
        Map<String, Object> metadata,
        List<String> keywords,
        boolean useLlmKeywordExtraction) {

    public AttachmentRagIndexCommand {
        documentId = normalize(documentId);
        objectType = normalize(objectType);
        objectId = normalize(objectId);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
