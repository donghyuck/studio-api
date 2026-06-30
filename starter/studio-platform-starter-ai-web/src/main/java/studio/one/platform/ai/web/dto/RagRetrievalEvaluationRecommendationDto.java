package studio.one.platform.ai.web.dto;

import java.util.List;

public record RagRetrievalEvaluationRecommendationDto(
        String questionSetId,
        String recommendedStrategy,
        String reason,
        int runCount,
        List<StrategyScore> strategies) {

    public record StrategyScore(
            String strategy,
            int runCount,
            int questionCount,
            double hitRate,
            double mrr,
            double averageElapsedMs,
            double score,
            List<String> runIds) {
    }
}
