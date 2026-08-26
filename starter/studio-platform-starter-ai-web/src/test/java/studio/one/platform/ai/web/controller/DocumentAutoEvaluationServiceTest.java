package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.Searchability;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.SearchabilityStatus;
import studio.one.platform.ai.core.rag.usability.MeasuredValue;
import studio.one.platform.ai.core.rag.usability.MeasurementState;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.web.dto.DocumentAutoEvaluationRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationResponseDto;

class DocumentAutoEvaluationServiceTest {

    @Test
    void generatesQuestionsOnlyFromCurrentRevisionAndRunsEvaluation() {
        DocumentUsabilityService usability = mock(DocumentUsabilityService.class);
        DocumentUsabilityAssessment assessment = mock(DocumentUsabilityAssessment.class);
        when(assessment.basis()).thenReturn(new DocumentUsabilityAssessment.Basis(
                "attachment", "17", "mdoc-1", "mrev-2", "hash-2", "cset-2", null, "space-1"));
        when(assessment.searchability()).thenReturn(new Searchability(
                MeasurementState.MEASURED, SearchabilityStatus.SEARCHABLE,
                MeasuredValue.measured(2L), List.of()));
        when(assessment.policy()).thenReturn(new DocumentUsabilityAssessment.Policy("policy-v1", "fingerprint"));
        when(usability.evaluate("attachment", "17")).thenReturn(assessment);

        VectorStorePort vectors = mock(VectorStorePort.class);
        when(vectors.listByObject("attachment", "17", 0, 200)).thenReturn(List.of(
                result("old", "이전 개정 내용", "mrev-1", "old-chunk", "이전"),
                result("current", "휴가 신청은 근무일 3일 전에 제출합니다.", "mrev-2", "new-chunk", "휴가 신청")));
        InMemoryRagRetrievalEvaluationQuestionSetStore sets = new InMemoryRagRetrievalEvaluationQuestionSetStore();
        InMemoryRagRetrievalEvaluationStore runs = new InMemoryRagRetrievalEvaluationStore();
        DocumentRagEvaluationProjectionService projection =
                new DocumentRagEvaluationProjectionService(runs, sets, new ObjectMapper());
        RagRetrievalEvaluationRunner runner = mock(RagRetrievalEvaluationRunner.class);
        when(runner.evaluate(any())).thenAnswer(invocation -> {
            RagRetrievalEvaluationRequestDto request = invocation.getArgument(0);
            return new RagRetrievalEvaluationResponseDto(
                    "run-1", Instant.now(), request.objectType(), request.objectId(), request.questionSetId(),
                    null, null, null, request.topK(), request.minScore(), List.of());
        });
        DocumentAutoEvaluationService service =
                new DocumentAutoEvaluationService(usability, vectors, sets, runner, projection);

        var response = service.evaluate(
                "attachment", "17", new DocumentAutoEvaluationRequestDto(5, List.of(), 5, 0.0d));

        assertThat(response.basis().revisionId()).isEqualTo("mrev-2");
        assertThat(response.questionSet().questions()).singleElement().satisfies(question -> {
            assertThat(question.query()).contains("휴가 신청");
            assertThat(question.expectedChunkIds()).containsExactly("new-chunk");
            assertThat(question.expectedContentContains()).containsExactly("휴가 신청은 근무일 3일 전에 제출합니다.");
        });
        assertThat(response.questionSet().description()).contains("DOCUMENT_AUTO_EVALUATION", "mrev-2");
        assertThat(response.result().questionSetId()).isEqualTo(response.questionSet().questionSetId());
    }

    private VectorSearchResult result(
            String id,
            String content,
            String revisionId,
            String chunkId,
            String sectionTitle) {
        return new VectorSearchResult(new VectorDocument(
                id,
                content,
                Map.of(
                        "markdownRevisionId", revisionId,
                        "chunkId", chunkId,
                        "sectionTitle", sectionTitle),
                List.of(0.1d)), 1.0d);
    }
}
