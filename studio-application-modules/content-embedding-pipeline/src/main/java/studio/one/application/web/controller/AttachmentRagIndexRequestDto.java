package studio.one.application.web.controller;

import java.util.List;
import java.util.Map;

/**
 * RAG 색인 시 외부에서 objectType/objectId/documentId 등을 전달받기 위한 DTO.
 */
public record AttachmentRagIndexRequestDto(
        String documentId,
        String objectType,
        String objectId,
        Map<String, Object> metadata,
        List<String> keywords,
        Boolean useLlmKeywordExtraction
) {
}
