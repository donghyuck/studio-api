package studio.one.platform.ai.web.dto;

import java.util.List;

public record DocumentAutoEvaluationResponseDto(
        String contractVersion,
        Basis basis,
        String generatorVersion,
        RagRetrievalEvaluationQuestionSetDto questionSet,
        RagRetrievalEvaluationResponseDto result) {

    public record Basis(
            String objectType,
            String objectId,
            String documentId,
            String revisionId,
            String sourceContentHash,
            String chunkSetId,
            String embeddingSpaceId,
            List<String> reasonCodes) {
    }
}
