package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.ai.web.dto.RagRetrievalEvaluationAnalysisDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationRecommendationDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationResponseDto;

public class RagRetrievalRecommendationService {

    public RagRetrievalEvaluationRecommendationDto recommend(
            String questionSetId,
            List<RagRetrievalEvaluationResponseDto> runs) {
        Map<String, MutableStrategyScore> scores = new LinkedHashMap<>();
        for (RagRetrievalEvaluationResponseDto run : runs == null ? List.<RagRetrievalEvaluationResponseDto>of() : runs) {
            for (RagRetrievalEvaluationResponseDto.StrategyResult strategy : run.strategies()) {
                scores.computeIfAbsent(strategy.strategy(), MutableStrategyScore::new)
                        .add(run.runId(), strategy);
            }
        }
        List<RagRetrievalEvaluationRecommendationDto.StrategyScore> strategyScores = scores.values().stream()
                .map(MutableStrategyScore::toScore)
                .sorted(Comparator.comparingDouble(RagRetrievalEvaluationRecommendationDto.StrategyScore::score)
                        .reversed()
                        .thenComparing(RagRetrievalEvaluationRecommendationDto.StrategyScore::averageElapsedMs)
                        .thenComparing(RagRetrievalEvaluationRecommendationDto.StrategyScore::strategy))
                .toList();
        String recommended = strategyScores.isEmpty() ? null : strategyScores.get(0).strategy();
        String reason = recommended == null
                ? "No completed evaluation runs were found for the question set"
                : "Selected by weighted score: hitRate 60%, MRR 35%, latency 5%";
        return new RagRetrievalEvaluationRecommendationDto(
                questionSetId,
                recommended,
                reason,
                runs == null ? 0 : runs.size(),
                strategyScores);
    }

    public RagRetrievalEvaluationAnalysisDto analyze(
            String questionSetId,
            List<RagRetrievalEvaluationResponseDto> runs) {
        List<RagRetrievalEvaluationResponseDto> safeRuns =
                runs == null ? List.of() : runs;
        Map<String, MutableStrategyAnalysis> strategies = new LinkedHashMap<>();
        Map<String, MutableQuestionAnalysis> questions = new LinkedHashMap<>();
        for (RagRetrievalEvaluationResponseDto run : safeRuns) {
            for (RagRetrievalEvaluationResponseDto.StrategyResult strategy : run.strategies()) {
                strategies.computeIfAbsent(strategy.strategy(), MutableStrategyAnalysis::new)
                        .add(strategy);
                for (RagRetrievalEvaluationResponseDto.QuestionResult question : strategy.questions()) {
                    questions.computeIfAbsent(question.query(), MutableQuestionAnalysis::new)
                            .add(strategy.strategy(), question);
                }
            }
        }
        List<RagRetrievalEvaluationAnalysisDto.StrategyAnalysis> strategyAnalyses = strategies.values().stream()
                .map(MutableStrategyAnalysis::toAnalysis)
                .sorted(Comparator.comparingDouble(RagRetrievalEvaluationAnalysisDto.StrategyAnalysis::hitRate)
                        .reversed()
                        .thenComparing(RagRetrievalEvaluationAnalysisDto.StrategyAnalysis::averageElapsedMs)
                        .thenComparing(RagRetrievalEvaluationAnalysisDto.StrategyAnalysis::strategy))
                .toList();
        List<RagRetrievalEvaluationAnalysisDto.QuestionAnalysis> questionAnalyses = questions.values().stream()
                .map(MutableQuestionAnalysis::toAnalysis)
                .sorted(Comparator.comparingInt((RagRetrievalEvaluationAnalysisDto.QuestionAnalysis question) ->
                                question.missedStrategies().size()).reversed()
                        .thenComparing(RagRetrievalEvaluationAnalysisDto.QuestionAnalysis::query))
                .toList();
        return new RagRetrievalEvaluationAnalysisDto(
                questionSetId,
                safeRuns.size(),
                questions.size(),
                strategyAnalyses,
                questionAnalyses);
    }

    private static final class MutableStrategyScore {
        private final String strategy;
        private final List<String> runIds = new ArrayList<>();
        private int runCount;
        private int questionCount;
        private double hitRateSum;
        private double mrrSum;
        private double elapsedSum;

        private MutableStrategyScore(String strategy) {
            this.strategy = strategy;
        }

        private void add(String runId, RagRetrievalEvaluationResponseDto.StrategyResult result) {
            runIds.add(runId);
            runCount++;
            questionCount += result.questionCount();
            hitRateSum += result.hitRate();
            mrrSum += result.mrr();
            elapsedSum += result.averageElapsedMs();
        }

        private RagRetrievalEvaluationRecommendationDto.StrategyScore toScore() {
            double hitRate = runCount == 0 ? 0.0d : hitRateSum / runCount;
            double mrr = runCount == 0 ? 0.0d : mrrSum / runCount;
            double elapsed = runCount == 0 ? 0.0d : elapsedSum / runCount;
            double latencyScore = elapsed <= 0.0d ? 1.0d : 1.0d / (1.0d + elapsed / 1000.0d);
            double score = hitRate * 0.60d + mrr * 0.35d + latencyScore * 0.05d;
            return new RagRetrievalEvaluationRecommendationDto.StrategyScore(
                    strategy,
                    runCount,
                    questionCount,
                    hitRate,
                    mrr,
                    elapsed,
                    score,
                    List.copyOf(runIds));
        }
    }

    private static final class MutableStrategyAnalysis {
        private final String strategy;
        private int runCount;
        private int questionCount;
        private int hitCount;
        private double reciprocalRankSum;
        private long elapsedSum;

        private MutableStrategyAnalysis(String strategy) {
            this.strategy = strategy;
        }

        private void add(RagRetrievalEvaluationResponseDto.StrategyResult result) {
            runCount++;
            questionCount += result.questionCount();
            hitCount += result.hitCount();
            for (RagRetrievalEvaluationResponseDto.QuestionResult question : result.questions()) {
                if (question.firstRelevantRank() != null && question.firstRelevantRank() > 0) {
                    reciprocalRankSum += 1.0d / question.firstRelevantRank();
                }
                elapsedSum += question.elapsedMs();
            }
        }

        private RagRetrievalEvaluationAnalysisDto.StrategyAnalysis toAnalysis() {
            return new RagRetrievalEvaluationAnalysisDto.StrategyAnalysis(
                    strategy,
                    runCount,
                    questionCount,
                    hitCount,
                    questionCount == 0 ? 0.0d : hitCount / (double) questionCount,
                    questionCount == 0 ? 0.0d : reciprocalRankSum / questionCount,
                    questionCount == 0 ? 0.0d : elapsedSum / (double) questionCount,
                    Math.max(0, questionCount - hitCount));
        }
    }

    private static final class MutableQuestionAnalysis {
        private final String query;
        private final List<String> hitStrategies = new ArrayList<>();
        private final List<String> missedStrategies = new ArrayList<>();
        private String bestStrategy;
        private Integer bestRank;

        private MutableQuestionAnalysis(String query) {
            this.query = query;
        }

        private void add(String strategy, RagRetrievalEvaluationResponseDto.QuestionResult question) {
            if (question.hit()) {
                hitStrategies.add(strategy);
                if (bestRank == null || question.firstRelevantRank() < bestRank) {
                    bestRank = question.firstRelevantRank();
                    bestStrategy = strategy;
                }
            } else {
                missedStrategies.add(strategy);
            }
        }

        private RagRetrievalEvaluationAnalysisDto.QuestionAnalysis toAnalysis() {
            return new RagRetrievalEvaluationAnalysisDto.QuestionAnalysis(
                    query,
                    List.copyOf(hitStrategies),
                    List.copyOf(missedStrategies),
                    bestStrategy,
                    bestRank);
        }
    }
}
