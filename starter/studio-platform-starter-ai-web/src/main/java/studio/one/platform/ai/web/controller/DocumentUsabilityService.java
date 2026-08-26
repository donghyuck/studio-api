package studio.one.platform.ai.web.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.lang.Nullable;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobFilter;
import studio.one.platform.ai.core.rag.RagIndexJobPageRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSort;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.Decision;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.DecisionCode;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.Eligibility;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.EligibilityStatus;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.EvaluationStatus;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.Execution;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.ExecutionStatus;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.Freshness;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.Indexing;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.RagEvaluation;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.Searchability;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.SearchabilityStatus;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.LocationEvidence;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.QualityEvidence;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.EligibilityEvidence;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.QualityStatus;
import studio.one.platform.ai.core.rag.usability.MeasuredValue;
import studio.one.platform.ai.core.rag.usability.MeasurementState;
import studio.one.platform.ai.core.rag.usability.RagObjectUsabilityEvidenceContributor;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagPipelineService;

/**
 * Produces one server-owned usability decision from source, index, and vector
 * evidence. Raw metadata remains available through the legacy metadata endpoint.
 */
public final class DocumentUsabilityService {
    private final RagIndexJobService jobService;
    private final RagPipelineService ragPipelineService;
    @Nullable
    private final VectorStorePort vectorStorePort;
    private final List<RagObjectUsabilityEvidenceContributor> evidenceContributors;
    private final DocumentUsabilityPolicyResolver policyResolver;
    @Nullable
    private final DocumentRagEvaluationProjectionService evaluationProjectionService;

    public DocumentUsabilityService(
            RagIndexJobService jobService,
            RagPipelineService ragPipelineService,
            @Nullable VectorStorePort vectorStorePort,
            List<RagObjectUsabilityEvidenceContributor> evidenceContributors,
            DocumentUsabilityPolicyResolver policyResolver) {
        this(jobService, ragPipelineService, vectorStorePort, evidenceContributors, policyResolver, null);
    }

    public DocumentUsabilityService(
            RagIndexJobService jobService,
            RagPipelineService ragPipelineService,
            @Nullable VectorStorePort vectorStorePort,
            List<RagObjectUsabilityEvidenceContributor> evidenceContributors,
            DocumentUsabilityPolicyResolver policyResolver,
            @Nullable DocumentRagEvaluationProjectionService evaluationProjectionService) {
        this.jobService = java.util.Objects.requireNonNull(jobService, "jobService");
        this.ragPipelineService = java.util.Objects.requireNonNull(ragPipelineService, "ragPipelineService");
        this.vectorStorePort = vectorStorePort;
        this.evidenceContributors = evidenceContributors == null ? List.of() : List.copyOf(evidenceContributors);
        this.policyResolver = policyResolver == null ? new DocumentUsabilityPolicyResolver() : policyResolver;
        this.evaluationProjectionService = evaluationProjectionService;
    }

    public DocumentUsabilityAssessment evaluate(String objectType, String objectId) {
        String normalizedObjectType = required(objectType, "objectType");
        String normalizedObjectId = required(objectId, "objectId");
        DocumentUsabilityEvidence evidence = evidence(normalizedObjectType, normalizedObjectId);
        JobRead jobRead = latestJob(normalizedObjectType, normalizedObjectId);
        RagIndexJob job = jobRead.job();
        MetadataRead metadataRead = vectorMetadata(normalizedObjectType, normalizedObjectId);

        DocumentUsabilityAssessment.Quality quality = quality(evidence.quality());
        DocumentUsabilityAssessment.Location location = location(evidence.location());
        Eligibility eligibility = eligibility(evidence.eligibility());
        Execution execution = execution(jobRead);
        Searchability searchability = searchability(
                normalizedObjectType, normalizedObjectId, evidence, job, metadataRead);
        RagEvaluation ragEvaluation = evaluationProjectionService == null
                ? notMeasuredEvaluation()
                : evaluationProjectionService.latest(
                        normalizedObjectType,
                        normalizedObjectId,
                        evidence.revisionId(),
                        evidence.sourceContentHash());
        Decision decision = decision(quality, eligibility, execution, searchability, ragEvaluation);

        return new DocumentUsabilityAssessment(
                DocumentUsabilityPolicyResolver.CONTRACT_VERSION,
                Instant.now(),
                new DocumentUsabilityAssessment.Basis(
                        normalizedObjectType,
                        normalizedObjectId,
                        evidence.documentId(),
                        evidence.revisionId(),
                        evidence.sourceContentHash(),
                        text(metadataRead.metadata().get("chunkSetId")),
                        job == null ? null : job.jobId(),
                        job == null ? null : job.embeddingSpaceId()),
                decision,
                quality,
                location,
                new Indexing(eligibility, execution),
                searchability,
                ragEvaluation,
                policyResolver.snapshot());
    }

    private DocumentUsabilityEvidence evidence(String objectType, String objectId) {
        for (RagObjectUsabilityEvidenceContributor contributor : evidenceContributors) {
            if (contributor.supports(objectType, objectId)) {
                try {
                    return contributor.contribute(objectType, objectId)
                            .orElseGet(() -> emptyEvidence(objectType, objectId, "DOCUMENT_EVIDENCE_NOT_FOUND"));
                } catch (RuntimeException ex) {
                    return emptyEvidence(objectType, objectId, "DOCUMENT_EVIDENCE_FAILED");
                }
            }
        }
        return emptyEvidence(objectType, objectId, "DOCUMENT_EVIDENCE_PROVIDER_UNAVAILABLE");
    }

    private DocumentUsabilityEvidence emptyEvidence(String objectType, String objectId, String reasonCode) {
        return new DocumentUsabilityEvidence(
                objectType,
                objectId,
                null,
                null,
                null,
                null,
                QualityEvidence.notMeasured(reasonCode),
                EligibilityEvidence.notMeasured(reasonCode),
                LocationEvidence.notMeasured(reasonCode));
    }

    private JobRead latestJob(String objectType, String objectId) {
        try {
            RagIndexJob job = jobService.listJobs(
                        new RagIndexJobFilter(null, objectType, objectId, null),
                        new RagIndexJobPageRequest(0, 1),
                        RagIndexJobSort.defaults())
                .jobs().stream()
                .findFirst()
                .orElse(null);
            return new JobRead(MeasurementState.MEASURED, job, null);
        } catch (RuntimeException ex) {
            return new JobRead(MeasurementState.FAILED, null, "INDEX_JOB_LOOKUP_FAILED");
        }
    }

    private DocumentUsabilityAssessment.Quality quality(QualityEvidence evidence) {
        return new DocumentUsabilityAssessment.Quality(
                evidence.state(), evidence.status(), evidence.score(), evidence.blocking(), evidence.reasonCodes());
    }

    private DocumentUsabilityAssessment.Location location(LocationEvidence evidence) {
        return new DocumentUsabilityAssessment.Location(
                evidence.state(), evidence.scheme(), evidence.coverage(), evidence.pageCoverage(),
                evidence.reasonCodes(), evidence.samples());
    }

    private Eligibility eligibility(EligibilityEvidence evidence) {
        if (evidence.state() != MeasurementState.MEASURED) {
            return new Eligibility(evidence.state(), EligibilityStatus.UNKNOWN, evidence.reasonCodes());
        }
        if (!Boolean.TRUE.equals(evidence.eligible())) {
            return new Eligibility(MeasurementState.MEASURED, EligibilityStatus.BLOCKED,
                    evidence.reasonCodes().isEmpty()
                            ? List.of("BLOCKING_QUALITY_FAILURE") : evidence.reasonCodes());
        }
        return new Eligibility(MeasurementState.MEASURED, EligibilityStatus.ELIGIBLE,
                evidence.reasonCodes().isEmpty()
                        ? List.of("NO_BLOCKING_QUALITY_FAILURE") : evidence.reasonCodes());
    }

    private Execution execution(JobRead jobRead) {
        RagIndexJob job = jobRead.job();
        if (jobRead.state() == MeasurementState.FAILED) {
            return new Execution(
                    MeasurementState.FAILED,
                    ExecutionStatus.FAILED,
                    null,
                    MeasuredValue.failed(jobRead.reasonCode()),
                    MeasuredValue.failed(jobRead.reasonCode()),
                    MeasuredValue.failed(jobRead.reasonCode()),
                    MeasuredValue.failed(jobRead.reasonCode()),
                    List.of(jobRead.reasonCode()));
        }
        if (job == null) {
            return new Execution(
                    MeasurementState.MEASURED,
                    ExecutionStatus.NOT_REQUESTED,
                    null,
                    MeasuredValue.notMeasured("INDEX_NOT_REQUESTED"),
                    MeasuredValue.notMeasured("INDEX_NOT_REQUESTED"),
                    MeasuredValue.notMeasured("INDEX_NOT_REQUESTED"),
                    MeasuredValue.notMeasured("INDEX_NOT_REQUESTED"),
                    List.of("INDEX_NOT_REQUESTED"));
        }
        boolean pending = job.status() == RagIndexJobStatus.PENDING;
        MeasuredValue<Integer> chunkCount = pending
                ? MeasuredValue.notMeasured("INDEX_NOT_STARTED") : MeasuredValue.measured(job.chunkCount());
        MeasuredValue<Integer> embeddedCount = pending
                ? MeasuredValue.notMeasured("INDEX_NOT_STARTED") : MeasuredValue.measured(job.embeddedCount());
        MeasuredValue<Integer> indexedCount = pending
                ? MeasuredValue.notMeasured("INDEX_NOT_STARTED") : MeasuredValue.measured(job.indexedCount());
        return new Execution(
                MeasurementState.MEASURED,
                ExecutionStatus.valueOf(job.status().name()),
                job.currentStep() == null ? null : job.currentStep().name(),
                progress(job),
                chunkCount,
                embeddedCount,
                indexedCount,
                job.errorMessage() == null ? List.of() : List.of("INDEX_EXECUTION_ERROR"));
    }

    private MeasuredValue<Double> progress(RagIndexJob job) {
        if (job.status() == RagIndexJobStatus.PENDING) {
            return MeasuredValue.notMeasured("INDEX_NOT_STARTED");
        }
        if (job.status() == RagIndexJobStatus.SUCCEEDED || job.status() == RagIndexJobStatus.WARNING) {
            return MeasuredValue.measured(1.0d);
        }
        if (job.chunkCount() <= 0) {
            return MeasuredValue.measured(0.0d);
        }
        return MeasuredValue.measured(Math.min(1.0d, job.indexedCount() / (double) job.chunkCount()));
    }

    private Searchability searchability(
            String objectType,
            String objectId,
            DocumentUsabilityEvidence evidence,
            RagIndexJob job,
            MetadataRead metadataRead) {
        if (vectorStorePort == null) {
            return new Searchability(
                    MeasurementState.FAILED,
                    SearchabilityStatus.UNKNOWN,
                    MeasuredValue.failed("VECTOR_STORE_UNAVAILABLE"),
                    List.of("VECTOR_STORE_UNAVAILABLE"));
        }
        final boolean exists;
        try {
            exists = vectorStorePort.exists(objectType, objectId);
        } catch (RuntimeException ex) {
            return new Searchability(
                    MeasurementState.FAILED,
                    SearchabilityStatus.UNKNOWN,
                    MeasuredValue.failed("VECTOR_EXISTENCE_CHECK_FAILED"),
                    List.of("VECTOR_EXISTENCE_CHECK_FAILED"));
        }
        if (!exists) {
            return new Searchability(
                    MeasurementState.MEASURED,
                    SearchabilityStatus.NOT_SEARCHABLE,
                    MeasuredValue.measured(0L),
                    List.of("NO_INDEXED_RECORDS"));
        }

        if (metadataRead.state() == MeasurementState.FAILED) {
            return new Searchability(
                    MeasurementState.FAILED,
                    SearchabilityStatus.UNKNOWN,
                    indexedCount(objectType, objectId),
                    List.of(metadataRead.reasonCode()));
        }
        Map<String, Object> metadata = metadataRead.metadata();
        String indexedRevisionId = firstText(metadata, "markdownRevisionId", "sourceRevisionId", "revisionId");
        if (evidence.revisionId() != null && indexedRevisionId == null) {
            return new Searchability(
                    MeasurementState.NOT_MEASURED,
                    SearchabilityStatus.UNKNOWN,
                    indexedCount(objectType, objectId),
                    List.of("INDEX_REVISION_UNVERIFIED"));
        }
        if (evidence.revisionId() != null && !evidence.revisionId().equals(indexedRevisionId)) {
            return new Searchability(
                    MeasurementState.MEASURED,
                    SearchabilityStatus.NOT_SEARCHABLE,
                    indexedCount(objectType, objectId),
                    List.of("STALE_INDEX_REVISION"));
        }
        if (job != null && job.embeddingSpaceId() != null) {
            String indexedSpaceId = firstText(metadata, "embeddingSpaceId", "resolvedEmbeddingSpaceId");
            if (indexedSpaceId != null && !job.embeddingSpaceId().equals(indexedSpaceId)) {
                return new Searchability(
                        MeasurementState.MEASURED,
                        SearchabilityStatus.NOT_SEARCHABLE,
                        indexedCount(objectType, objectId),
                        List.of("STALE_EMBEDDING_SPACE"));
            }
        }
        return new Searchability(
                MeasurementState.MEASURED,
                SearchabilityStatus.SEARCHABLE,
                indexedCount(objectType, objectId),
                List.of("ACTIVE_INDEX_CONFIRMED"));
    }

    private MetadataRead vectorMetadata(String objectType, String objectId) {
        if (vectorStorePort == null) {
            return new MetadataRead(MeasurementState.FAILED, Map.of(), "VECTOR_STORE_UNAVAILABLE");
        }
        try {
            Map<String, Object> metadata = vectorStorePort.getMetadata(objectType, objectId);
            return new MetadataRead(
                    MeasurementState.MEASURED, metadata == null ? Map.of() : metadata, null);
        } catch (UnsupportedOperationException ex) {
            return new MetadataRead(
                    MeasurementState.NOT_MEASURED, Map.of(), "VECTOR_METADATA_UNSUPPORTED");
        } catch (RuntimeException ex) {
            return new MetadataRead(MeasurementState.FAILED, Map.of(), "VECTOR_METADATA_LOOKUP_FAILED");
        }
    }

    private MeasuredValue<Long> indexedCount(String objectType, String objectId) {
        try {
            return MeasuredValue.measured(ragPipelineService.countByObject(objectType, objectId));
        } catch (RuntimeException ex) {
            return MeasuredValue.failed("INDEXED_RECORD_COUNT_FAILED");
        }
    }

    private RagEvaluation notMeasuredEvaluation() {
        return new RagEvaluation(
                MeasurementState.NOT_MEASURED,
                EvaluationStatus.NOT_RUN,
                Freshness.UNKNOWN,
                null,
                null,
                null,
                null,
                MeasuredValue.notMeasured("RAG_EVALUATION_NOT_RUN"),
                MeasuredValue.notMeasured("RAG_EVALUATION_NOT_RUN"),
                MeasuredValue.notMeasured("RAG_EVALUATION_NOT_RUN"),
                MeasuredValue.notMeasured("RAG_EVALUATION_NOT_RUN"),
                List.of("RAG_EVALUATION_NOT_RUN"));
    }

    private Decision decision(
            DocumentUsabilityAssessment.Quality quality,
            Eligibility eligibility,
            Execution execution,
            Searchability searchability,
            RagEvaluation ragEvaluation) {
        List<String> reasons = new ArrayList<>();
        reasons.addAll(quality.reasonCodes());
        reasons.addAll(eligibility.reasonCodes());
        reasons.addAll(execution.reasonCodes());
        reasons.addAll(searchability.reasonCodes());
        reasons.addAll(ragEvaluation.reasonCodes());

        if (quality.blocking() || eligibility.status() == EligibilityStatus.BLOCKED) {
            return new Decision(DecisionCode.NOT_AVAILABLE, false, reasons);
        }
        if (searchability.status() == SearchabilityStatus.SEARCHABLE) {
            boolean review = quality.status() != QualityStatus.PASSED
                    || ragEvaluation.state() != MeasurementState.MEASURED
                    || ragEvaluation.freshness() != Freshness.CURRENT;
            return new Decision(review ? DecisionCode.AVAILABLE_WITH_REVIEW : DecisionCode.AVAILABLE, true, reasons);
        }
        if (execution.status() == ExecutionStatus.PENDING || execution.status() == ExecutionStatus.RUNNING) {
            return new Decision(DecisionCode.PREPARING, false, reasons);
        }
        if (execution.status() == ExecutionStatus.FAILED || execution.status() == ExecutionStatus.CANCELLED) {
            return new Decision(DecisionCode.NOT_AVAILABLE, false, reasons);
        }
        if (eligibility.status() == EligibilityStatus.ELIGIBLE
                && execution.status() == ExecutionStatus.NOT_REQUESTED) {
            return new Decision(DecisionCode.READY_FOR_INDEXING, false, reasons);
        }
        if (searchability.status() == SearchabilityStatus.NOT_SEARCHABLE
                && execution.status() == ExecutionStatus.SUCCEEDED) {
            return new Decision(DecisionCode.NOT_AVAILABLE, false, reasons);
        }
        return new Decision(DecisionCode.UNKNOWN, false, reasons);
    }

    private String firstText(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            String value = text(metadata.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private String required(String value, String name) {
        String normalized = text(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private record JobRead(MeasurementState state, RagIndexJob job, String reasonCode) {
    }

    private record MetadataRead(MeasurementState state, Map<String, Object> metadata, String reasonCode) {
    }
}
