package studio.one.platform.ai.web.controller;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.EvaluationStatus;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.Freshness;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.RagEvaluation;
import studio.one.platform.ai.core.rag.usability.MeasuredValue;
import studio.one.platform.ai.core.rag.usability.MeasurementState;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationQuestionSetDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationResponseDto;

/** Reads persisted automatic retrieval runs and projects them into usability. */
public final class DocumentRagEvaluationProjectionService {
    static final String EVALUATION_KIND = "DOCUMENT_AUTO_EVALUATION";
    static final String GENERATOR_VERSION = "document-chunk-question-v1";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RagRetrievalEvaluationStore evaluationStore;
    private final RagRetrievalEvaluationQuestionSetStore questionSetStore;
    private final ObjectMapper objectMapper;

    public DocumentRagEvaluationProjectionService(
            RagRetrievalEvaluationStore evaluationStore,
            RagRetrievalEvaluationQuestionSetStore questionSetStore,
            ObjectMapper objectMapper) {
        this.evaluationStore = java.util.Objects.requireNonNull(evaluationStore, "evaluationStore");
        this.questionSetStore = java.util.Objects.requireNonNull(questionSetStore, "questionSetStore");
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public RagEvaluation latest(String objectType, String objectId, String revisionId, String sourceContentHash) {
        RagRetrievalEvaluationResponseDto run = evaluationStore.listByObject(objectType, objectId).stream()
                .filter(candidate -> automaticMetadata(candidate.questionSetId()) != null)
                .max(Comparator.comparing(RagRetrievalEvaluationResponseDto::createdAt))
                .orElse(null);
        if (run == null) {
            return notRun();
        }
        Map<String, Object> metadata = automaticMetadata(run.questionSetId());
        String evaluatedRevision = text(metadata.get("revisionId"));
        String evaluatedHash = text(metadata.get("sourceContentHash"));
        Freshness freshness = freshness(revisionId, sourceContentHash, evaluatedRevision, evaluatedHash);
        RagRetrievalEvaluationResponseDto.StrategyResult best = run.strategies().stream()
                .max(Comparator.comparingDouble(RagRetrievalEvaluationResponseDto.StrategyResult::hitRate)
                        .thenComparingDouble(RagRetrievalEvaluationResponseDto.StrategyResult::mrr))
                .orElse(null);
        if (best == null) {
            return new RagEvaluation(
                    MeasurementState.FAILED, EvaluationStatus.FAILED,
                    freshness,
                    run.questionSetId(), run.runId(),
                    run.topK(), null,
                    MeasuredValue.failed("EVALUATION_RESULT_EMPTY"),
                    MeasuredValue.failed("EVALUATION_RESULT_EMPTY"),
                    MeasuredValue.notMeasured("ANSWER_EVALUATION_NOT_RUN"),
                    MeasuredValue.notMeasured("ANSWER_EVALUATION_NOT_RUN"),
                    List.of("EVALUATION_RESULT_EMPTY"));
        }
        return new RagEvaluation(
                MeasurementState.MEASURED,
                EvaluationStatus.COMPLETED,
                freshness,
                run.questionSetId(),
                run.runId(),
                run.topK(),
                best.strategy(),
                MeasuredValue.measured(best.hitRate()),
                MeasuredValue.measured(best.mrr()),
                MeasuredValue.notMeasured("ANSWER_EVALUATION_NOT_RUN"),
                MeasuredValue.notMeasured("ANSWER_EVALUATION_NOT_RUN"),
                freshness == Freshness.CURRENT
                        ? List.of("BEST_RETRIEVAL_STRATEGY_SUMMARY")
                        : freshness == Freshness.STALE
                                ? List.of("BEST_RETRIEVAL_STRATEGY_SUMMARY", "EVALUATION_BASIS_STALE")
                                : List.of("BEST_RETRIEVAL_STRATEGY_SUMMARY", "EVALUATION_BASIS_UNVERIFIED"));
    }

    public String metadataJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to serialize automatic evaluation basis", ex);
        }
    }

    private Map<String, Object> automaticMetadata(String questionSetId) {
        if (questionSetId == null || questionSetId.isBlank()) {
            return null;
        }
        RagRetrievalEvaluationQuestionSetDto questionSet = questionSetStore.find(questionSetId).orElse(null);
        if (questionSet == null || questionSet.description() == null) {
            return null;
        }
        try {
            Map<String, Object> metadata = objectMapper.readValue(questionSet.description(), MAP_TYPE);
            return EVALUATION_KIND.equals(text(metadata.get("kind"))) ? metadata : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private RagEvaluation notRun() {
        return new RagEvaluation(
                MeasurementState.NOT_MEASURED, EvaluationStatus.NOT_RUN, Freshness.UNKNOWN, null, null,
                null, null,
                MeasuredValue.notMeasured("RAG_EVALUATION_NOT_RUN"),
                MeasuredValue.notMeasured("RAG_EVALUATION_NOT_RUN"),
                MeasuredValue.notMeasured("RAG_EVALUATION_NOT_RUN"),
                MeasuredValue.notMeasured("RAG_EVALUATION_NOT_RUN"),
                List.of("RAG_EVALUATION_NOT_RUN"));
    }

    private Freshness freshness(
            String currentRevision,
            String currentHash,
            String evaluatedRevision,
            String evaluatedHash) {
        if (currentRevision == null || evaluatedRevision == null) {
            return Freshness.UNKNOWN;
        }
        if (!currentRevision.equals(evaluatedRevision)) {
            return Freshness.STALE;
        }
        if (currentHash != null && evaluatedHash != null && !currentHash.equals(evaluatedHash)) {
            return Freshness.STALE;
        }
        return Freshness.CURRENT;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }
}
