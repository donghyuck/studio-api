package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.Freshness;
import studio.one.platform.ai.core.rag.usability.MeasurementState;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationQuestionSetDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationResponseDto;

class DocumentRagEvaluationProjectionServiceTest {

    @Test
    void ignoresLegacyRunWithoutQuestionSet() {
        InMemoryRagRetrievalEvaluationStore runs = new InMemoryRagRetrievalEvaluationStore();
        RagRetrievalEvaluationQuestionSetStore sets = mock(RagRetrievalEvaluationQuestionSetStore.class);
        DocumentRagEvaluationProjectionService service =
                new DocumentRagEvaluationProjectionService(runs, sets, new ObjectMapper());
        runs.save(new RagRetrievalEvaluationResponseDto(
                "legacy-run", Instant.now(), "attachment", "6", null,
                null, null, null, 5, 0.0d,
                List.of(new RagRetrievalEvaluationResponseDto.StrategyResult(
                        "semantic", 5, 0, 0.0d, 0.0d, 1.0d, List.of()))));

        var result = service.latest("attachment", "6", "mrev-6", "hash-6");

        assertThat(result.state()).isEqualTo(MeasurementState.NOT_MEASURED);
        assertThat(result.reasonCodes()).containsExactly("RAG_EVALUATION_NOT_RUN");
        verifyNoInteractions(sets);
    }

    @Test
    void projectsCurrentRevisionRunAndMarksLaterRevisionStale() {
        InMemoryRagRetrievalEvaluationStore runs = new InMemoryRagRetrievalEvaluationStore();
        InMemoryRagRetrievalEvaluationQuestionSetStore sets = new InMemoryRagRetrievalEvaluationQuestionSetStore();
        DocumentRagEvaluationProjectionService service =
                new DocumentRagEvaluationProjectionService(runs, sets, new ObjectMapper());
        String description = service.metadataJson(Map.of(
                "kind", DocumentRagEvaluationProjectionService.EVALUATION_KIND,
                "revisionId", "mrev-1",
                "sourceContentHash", "hash-1"));
        sets.save(new RagRetrievalEvaluationQuestionSetDto(
                "qset-1", "auto", description, Instant.now(), Instant.now(), List.of()));
        runs.save(new RagRetrievalEvaluationResponseDto(
                "run-1", Instant.now(), "attachment", "17", "qset-1",
                null, null, null, 5, 0.0d,
                List.of(new RagRetrievalEvaluationResponseDto.StrategyResult(
                        "hybrid", 5, 4, 0.8d, 0.7d, 10.0d, List.of()))));

        var current = service.latest("attachment", "17", "mrev-1", "hash-1");
        var stale = service.latest("attachment", "17", "mrev-2", "hash-2");

        assertThat(current.state()).isEqualTo(MeasurementState.MEASURED);
        assertThat(current.freshness()).isEqualTo(Freshness.CURRENT);
        assertThat(current.topK()).isEqualTo(5);
        assertThat(current.selectedStrategy()).isEqualTo("hybrid");
        assertThat(current.evidenceHitRate().value()).isEqualTo(0.8d);
        assertThat(current.mrr().value()).isEqualTo(0.7d);
        assertThat(current.groundedAnswerRate().state()).isEqualTo(MeasurementState.NOT_MEASURED);
        assertThat(stale.freshness()).isEqualTo(Freshness.STALE);
    }
}
