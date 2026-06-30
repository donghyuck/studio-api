package studio.one.platform.ai.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RagRetrievalEvaluationResponseDto(
        String runId,
        Instant createdAt,
        String objectType,
        String objectId,
        String questionSetId,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        Integer topK,
        Double minScore,
        List<StrategyResult> strategies) {

    public record StrategyResult(
            String strategy,
            int questionCount,
            int hitCount,
            double hitRate,
            double mrr,
            double averageElapsedMs,
            List<QuestionResult> questions) {
    }

    public record QuestionResult(
            String query,
            boolean hit,
            Integer firstRelevantRank,
            long elapsedMs,
            List<ResultItem> results) {
    }

    public record ResultItem(
            int rank,
            String chunkId,
            String documentChunkId,
            String documentId,
            double score,
            Map<String, Object> metadataPreview) {
    }
}
