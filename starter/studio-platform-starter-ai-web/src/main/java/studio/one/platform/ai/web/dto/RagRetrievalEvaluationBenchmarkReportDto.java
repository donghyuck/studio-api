package studio.one.platform.ai.web.dto;

import java.time.Instant;
import java.util.List;

public record RagRetrievalEvaluationBenchmarkReportDto(
        String questionSetId,
        Instant generatedAt,
        int objectCount,
        int runCount,
        RagRetrievalEvaluationRecommendationDto aggregateRecommendation,
        RagRetrievalEvaluationAnalysisDto aggregateAnalysis,
        List<ObjectReport> objects) {

    public record ObjectReport(
            String objectType,
            String objectId,
            String label,
            int runCount,
            List<String> runIds,
            RagRetrievalEvaluationRecommendationDto recommendation,
            RagRetrievalEvaluationAnalysisDto analysis) {
    }
}
