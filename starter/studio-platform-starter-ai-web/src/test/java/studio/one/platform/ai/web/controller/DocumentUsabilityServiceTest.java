package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagIndexJobPage;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.DecisionCode;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.EligibilityStatus;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.SearchabilityStatus;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.LocationEvidence;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.EligibilityEvidence;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.LocationScheme;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.QualityEvidence;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.QualityStatus;
import studio.one.platform.ai.core.rag.usability.MeasuredValue;
import studio.one.platform.ai.core.rag.usability.MeasurementState;
import studio.one.platform.ai.core.rag.usability.RagObjectUsabilityEvidenceContributor;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagPipelineService;

class DocumentUsabilityServiceTest {

    @Test
    void lowQualityScoreCanRemainEligibleAndSearchable() {
        RagIndexJobService jobs = emptyJobs();
        RagPipelineService pipeline = mock(RagPipelineService.class);
        VectorStorePort vectors = mock(VectorStorePort.class);
        when(vectors.exists("attachment", "17")).thenReturn(true);
        when(vectors.getMetadata("attachment", "17"))
                .thenReturn(Map.of("markdownRevisionId", "mrev-1", "chunkSetId", "cset-1"));
        when(pipeline.countByObject("attachment", "17")).thenReturn(8L);

        var assessment = service(jobs, pipeline, vectors, evidence(false, 0.7d)).evaluate("attachment", "17");

        assertThat(assessment.quality().score().value()).isEqualTo(0.7d);
        assertThat(assessment.quality().status()).isEqualTo(QualityStatus.REVIEW_REQUIRED);
        assertThat(assessment.indexing().eligibility().status()).isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(assessment.searchability().status()).isEqualTo(SearchabilityStatus.SEARCHABLE);
        assertThat(assessment.decision().code()).isEqualTo(DecisionCode.AVAILABLE_WITH_REVIEW);
        assertThat(assessment.decision().usable()).isTrue();
        assertThat(assessment.policy().version()).startsWith("document-usability-v1:");
    }

    @Test
    void blockingFailureOverridesExistingVectors() {
        RagIndexJobService jobs = emptyJobs();
        RagPipelineService pipeline = mock(RagPipelineService.class);
        VectorStorePort vectors = mock(VectorStorePort.class);
        when(vectors.exists("attachment", "17")).thenReturn(true);
        when(vectors.getMetadata("attachment", "17"))
                .thenReturn(Map.of("markdownRevisionId", "mrev-1"));
        when(pipeline.countByObject("attachment", "17")).thenReturn(8L);

        var assessment = service(jobs, pipeline, vectors, evidence(true, 0.0d)).evaluate("attachment", "17");

        assertThat(assessment.indexing().eligibility().status()).isEqualTo(EligibilityStatus.BLOCKED);
        assertThat(assessment.decision().code()).isEqualTo(DecisionCode.NOT_AVAILABLE);
        assertThat(assessment.decision().usable()).isFalse();
    }

    @Test
    void missingVectorAdapterIsFailureNotMeasuredZero() {
        RagPipelineService pipeline = mock(RagPipelineService.class);

        var assessment = service(emptyJobs(), pipeline, null, evidence(false, 1.0d))
                .evaluate("attachment", "17");

        assertThat(assessment.searchability().state()).isEqualTo(MeasurementState.FAILED);
        assertThat(assessment.searchability().indexedRecordCount().state()).isEqualTo(MeasurementState.FAILED);
        assertThat(assessment.searchability().indexedRecordCount().value()).isNull();
    }

    private DocumentUsabilityService service(
            RagIndexJobService jobs,
            RagPipelineService pipeline,
            VectorStorePort vectors,
            DocumentUsabilityEvidence evidence) {
        RagObjectUsabilityEvidenceContributor contributor = new RagObjectUsabilityEvidenceContributor() {
            @Override
            public boolean supports(String objectType, String objectId) {
                return true;
            }

            @Override
            public Optional<DocumentUsabilityEvidence> contribute(String objectType, String objectId) {
                return Optional.of(evidence);
            }
        };
        return new DocumentUsabilityService(
                jobs, pipeline, vectors, List.of(contributor), new DocumentUsabilityPolicyResolver());
    }

    private RagIndexJobService emptyJobs() {
        RagIndexJobService jobs = mock(RagIndexJobService.class);
        when(jobs.listJobs(any(), any(), any())).thenReturn(new RagIndexJobPage(List.of(), 0, 0, 1));
        return jobs;
    }

    private DocumentUsabilityEvidence evidence(boolean blocking, double score) {
        QualityEvidence quality = new QualityEvidence(
                MeasurementState.MEASURED,
                blocking ? QualityStatus.FAILED : QualityStatus.REVIEW_REQUIRED,
                MeasuredValue.measured(score),
                blocking,
                blocking ? List.of("QUALITY_GATE_BLOCKED") : List.of("LOW_SCORE_REVIEW"));
        LocationEvidence location = new LocationEvidence(
                MeasurementState.MEASURED,
                LocationScheme.PAGE,
                MeasuredValue.measured(1.0d),
                MeasuredValue.measured(1.0d),
                List.of(),
                List.of());
        return new DocumentUsabilityEvidence(
                "attachment", "17", "mdoc-1", "mrev-1", "source-hash", "pdf", quality,
                EligibilityEvidence.measured(
                        !blocking,
                        List.of(blocking ? "BLOCKING_QUALITY_FAILURE" : "NO_BLOCKING_QUALITY_FAILURE")),
                location);
    }
}
