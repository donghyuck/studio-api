package studio.one.platform.ai.core.rag.usability;

import java.time.Instant;
import java.util.List;

import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.DocumentLocationRef;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.LocationScheme;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.QualityStatus;

/**
 * Server-owned interpretation of whether a RAG object can be used now.
 */
public record DocumentUsabilityAssessment(
        String contractVersion,
        Instant evaluatedAt,
        Basis basis,
        Decision decision,
        Quality quality,
        Location location,
        Indexing indexing,
        Searchability searchability,
        RagEvaluation ragEvaluation,
        Policy policy) {

    public DocumentUsabilityAssessment {
        contractVersion = normalize(contractVersion);
        evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
    }

    public record Basis(
            String objectType,
            String objectId,
            String documentId,
            String revisionId,
            String sourceContentHash,
            String chunkSetId,
            String indexJobId,
            String embeddingSpaceId) {
    }

    public record Decision(DecisionCode code, boolean usable, List<String> reasonCodes) {
        public Decision {
            code = code == null ? DecisionCode.UNKNOWN : code;
            reasonCodes = normalized(reasonCodes);
        }
    }

    public record Quality(
            MeasurementState state,
            QualityStatus status,
            MeasuredValue<Double> score,
            boolean blocking,
            List<String> reasonCodes) {
        public Quality {
            reasonCodes = normalized(reasonCodes);
        }
    }

    public record Location(
            MeasurementState state,
            LocationScheme scheme,
            MeasuredValue<Double> coverage,
            MeasuredValue<Double> pageCoverage,
            List<String> reasonCodes,
            List<DocumentLocationRef> samples) {
        public Location {
            reasonCodes = normalized(reasonCodes);
            samples = samples == null ? List.of() : List.copyOf(samples);
        }
    }

    public record Indexing(Eligibility eligibility, Execution execution) {
    }

    public record Eligibility(
            MeasurementState state,
            EligibilityStatus status,
            List<String> reasonCodes) {
        public Eligibility {
            state = state == null ? MeasurementState.NOT_MEASURED : state;
            status = status == null ? EligibilityStatus.UNKNOWN : status;
            reasonCodes = normalized(reasonCodes);
        }
    }

    public record Execution(
            MeasurementState state,
            ExecutionStatus status,
            String currentStep,
            MeasuredValue<Double> progress,
            MeasuredValue<Integer> chunkCount,
            MeasuredValue<Integer> embeddedCount,
            MeasuredValue<Integer> indexedCount,
            List<String> reasonCodes) {
        public Execution {
            state = state == null ? MeasurementState.NOT_MEASURED : state;
            status = status == null ? ExecutionStatus.NOT_REQUESTED : status;
            reasonCodes = normalized(reasonCodes);
        }
    }

    public record Searchability(
            MeasurementState state,
            SearchabilityStatus status,
            MeasuredValue<Long> indexedRecordCount,
            List<String> reasonCodes) {
        public Searchability {
            state = state == null ? MeasurementState.NOT_MEASURED : state;
            status = status == null ? SearchabilityStatus.UNKNOWN : status;
            reasonCodes = normalized(reasonCodes);
        }
    }

    public record RagEvaluation(
            MeasurementState state,
            EvaluationStatus status,
            Freshness freshness,
            String questionSetVersionId,
            String runId,
            Integer topK,
            String selectedStrategy,
            MeasuredValue<Double> evidenceHitRate,
            MeasuredValue<Double> mrr,
            MeasuredValue<Double> groundedAnswerRate,
            MeasuredValue<Double> citationAccuracy,
            List<String> reasonCodes) {
        public RagEvaluation {
            state = state == null ? MeasurementState.NOT_MEASURED : state;
            status = status == null ? EvaluationStatus.NOT_RUN : status;
            freshness = freshness == null ? Freshness.UNKNOWN : freshness;
            reasonCodes = normalized(reasonCodes);
        }
    }

    public record Policy(String version, String fingerprint) {
    }

    public enum DecisionCode {
        AVAILABLE,
        AVAILABLE_WITH_REVIEW,
        PREPARING,
        READY_FOR_INDEXING,
        NOT_AVAILABLE,
        UNKNOWN
    }

    public enum EligibilityStatus {
        ELIGIBLE,
        BLOCKED,
        UNKNOWN
    }

    public enum ExecutionStatus {
        NOT_REQUESTED,
        PENDING,
        RUNNING,
        SUCCEEDED,
        WARNING,
        FAILED,
        CANCELLED
    }

    public enum SearchabilityStatus {
        SEARCHABLE,
        NOT_SEARCHABLE,
        UNKNOWN
    }

    public enum EvaluationStatus {
        NOT_RUN,
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public enum Freshness {
        CURRENT,
        STALE,
        UNKNOWN
    }

    private static List<String> normalized(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
