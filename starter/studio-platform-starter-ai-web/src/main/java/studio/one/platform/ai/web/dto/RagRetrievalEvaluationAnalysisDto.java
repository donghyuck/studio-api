package studio.one.platform.ai.web.dto;

import java.util.List;

public record RagRetrievalEvaluationAnalysisDto(
        String questionSetId,
        int runCount,
        int questionCount,
        List<StrategyAnalysis> strategies,
        List<QuestionAnalysis> questions
) {

    public record StrategyAnalysis(
            String strategy,
            int runCount,
            int questionCount,
            int hitCount,
            double hitRate,
            double mrr,
            double averageElapsedMs,
            int failedQuestionCount
    ) {
    }

    public record QuestionAnalysis(
            String query,
            List<String> hitStrategies,
            List<String> missedStrategies,
            String bestStrategy,
            Integer bestRank
    ) {
    }
}
