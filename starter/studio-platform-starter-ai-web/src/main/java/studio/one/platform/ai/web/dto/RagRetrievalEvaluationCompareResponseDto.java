package studio.one.platform.ai.web.dto;

import java.util.List;

public record RagRetrievalEvaluationCompareResponseDto(
        String beforeRunId,
        String afterRunId,
        List<StrategyDelta> strategies) {

    public record StrategyDelta(
            String strategy,
            int beforeQuestionCount,
            int afterQuestionCount,
            double beforeHitRate,
            double afterHitRate,
            double hitRateDelta,
            double beforeMrr,
            double afterMrr,
            double mrrDelta,
            double beforeAverageElapsedMs,
            double afterAverageElapsedMs,
            double averageElapsedMsDelta) {
    }
}
