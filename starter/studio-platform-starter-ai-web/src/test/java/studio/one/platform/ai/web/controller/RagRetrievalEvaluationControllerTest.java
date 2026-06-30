package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationCompareRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationBenchmarkRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationQuestionSetDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationQuestionSetRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationQuestionSetRunRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationResponseDto;
import studio.one.platform.ai.web.dto.RagRetrievalPolicyApplyRecommendationRequestDto;

class RagRetrievalEvaluationControllerTest {

    @Test
    void evaluatePersistsRunAndAllowsLookup() {
        RagChatRetrievalService retrievalService = mock(RagChatRetrievalService.class);
        when(retrievalService.retrieve(any(), eq("휴가 규정"), eq("attachment"), eq("1"), eq(5), eq(0.6d), eq(5),
                eq(false)))
                .thenReturn(new RagChatRetrievalService.RetrievalResult(
                        List.of(new RagSearchResult("doc-1", "여름 휴가 규정", Map.of("chunkId", "chunk-1"), 0.9d)),
                        RagChatRetrievalService.RetrievalDebug.disabled()));
        RagRetrievalEvaluationStore store = new InMemoryRagRetrievalEvaluationStore();
        RagRetrievalEvaluationRunner runner = new RagRetrievalEvaluationRunner(retrievalService, store);
        InMemoryRagRetrievalEvaluationQuestionSetStore questionSetStore =
                new InMemoryRagRetrievalEvaluationQuestionSetStore();
        RagRetrievalEvaluationController controller = new RagRetrievalEvaluationController(
                runner,
                store,
                new RagRetrievalEvaluationJobService(
                        runner,
                        Runnable::run,
                        new InMemoryRagRetrievalEvaluationJobStore()),
                questionSetStore,
                new RagRetrievalRecommendationService());

        RagRetrievalEvaluationResponseDto evaluated = controller.evaluate(request()).getBody().getData();

        assertThat(evaluated.runId()).startsWith("reval-");
        assertThat(evaluated.strategies()).singleElement()
                .satisfies(strategy -> assertThat(strategy.hitRate()).isEqualTo(1.0d));
        assertThat(controller.get(evaluated.runId()).getBody().getData().runId()).isEqualTo(evaluated.runId());
        assertThat(controller.list().getBody().getData()).hasSize(1);
    }

    @Test
    void applyRecommendationRejectsLowQualityRecommendation() {
        InMemoryRagRetrievalEvaluationQuestionSetStore questionSetStore =
                new InMemoryRagRetrievalEvaluationQuestionSetStore();
        questionSetStore.save(new RagRetrievalEvaluationQuestionSetDto(
                "qset-low",
                "low quality",
                null,
                Instant.now(),
                Instant.now(),
                request().questions()));
        RagRetrievalEvaluationStore store = new InMemoryRagRetrievalEvaluationStore();
        store.save(new RagRetrievalEvaluationResponseDto(
                "run-low",
                Instant.now(),
                "attachment",
                "3",
                "qset-low",
                "retrieval-ko-kure",
                null,
                null,
                5,
                0.0d,
                List.of(new RagRetrievalEvaluationResponseDto.StrategyResult(
                        "ideaBlock",
                        10,
                        2,
                        0.2d,
                        0.2d,
                        100.0d,
                        List.of()))));
        RagRetrievalPolicyController policyController = new RagRetrievalPolicyController(
                new InMemoryRagRetrievalPolicyStore(),
                store,
                questionSetStore,
                new RagRetrievalRecommendationService(),
                new studio.one.platform.ai.autoconfigure.AiWebRagProperties.RetrievalProperties(),
                new InMemoryRagRetrievalPolicyUsageStore(),
                new InMemoryRagRetrievalPolicyHistoryStore());

        assertThatThrownBy(() -> policyController.applyRecommendation(new RagRetrievalPolicyApplyRecommendationRequestDto(
                "qset-low",
                "attachment",
                "3")))
                .isInstanceOf(RagRetrievalPolicyException.class)
                .satisfies(ex -> assertThat(((RagRetrievalPolicyException) ex).code())
                        .isEqualTo("RETRIEVAL_RECOMMENDATION_QUALITY_TOO_LOW"));
    }

    @Test
    void applyRecommendationUsesOnlyRunsForRequestedObject() {
        InMemoryRagRetrievalEvaluationQuestionSetStore questionSetStore =
                new InMemoryRagRetrievalEvaluationQuestionSetStore();
        questionSetStore.save(new RagRetrievalEvaluationQuestionSetDto(
                "qset-mixed",
                "mixed objects",
                null,
                Instant.now(),
                Instant.now(),
                request().questions()));
        RagRetrievalEvaluationStore store = new InMemoryRagRetrievalEvaluationStore();
        store.save(new RagRetrievalEvaluationResponseDto(
                "run-object-1",
                Instant.now(),
                "attachment",
                "1",
                "qset-mixed",
                "retrieval-ko-kure",
                null,
                null,
                5,
                0.0d,
                List.of(new RagRetrievalEvaluationResponseDto.StrategyResult(
                        "structure",
                        10,
                        10,
                        1.0d,
                        1.0d,
                        100.0d,
                        List.of()))));
        store.save(new RagRetrievalEvaluationResponseDto(
                "run-object-2",
                Instant.now(),
                "attachment",
                "2",
                "qset-mixed",
                "retrieval-ko-kure",
                null,
                null,
                5,
                0.0d,
                List.of(new RagRetrievalEvaluationResponseDto.StrategyResult(
                        "ideaBlock",
                        10,
                        10,
                        1.0d,
                        0.95d,
                        50.0d,
                        List.of()))));
        RagRetrievalPolicyController policyController = new RagRetrievalPolicyController(
                new InMemoryRagRetrievalPolicyStore(),
                store,
                questionSetStore,
                new RagRetrievalRecommendationService(),
                new studio.one.platform.ai.autoconfigure.AiWebRagProperties.RetrievalProperties(),
                new InMemoryRagRetrievalPolicyUsageStore(),
                new InMemoryRagRetrievalPolicyHistoryStore());

        var policy = policyController.applyRecommendation(new RagRetrievalPolicyApplyRecommendationRequestDto(
                "qset-mixed",
                "attachment",
                "2")).getBody().getData();

        assertThat(policy.retrievalStrategy()).isEqualTo("ideaBlock");
        assertThat(policy.evaluationRunId()).isEqualTo("run-object-2");
        assertThat(policy.hitRate()).isEqualTo(1.0d);
        assertThat(policy.mrr()).isEqualTo(0.95d);
    }

    @Test
    void compareReturnsStrategyDeltasForStoredRuns() {
        RagChatRetrievalService retrievalService = mock(RagChatRetrievalService.class);
        when(retrievalService.retrieve(any(), eq("휴가 규정"), eq("attachment"), eq("1"), eq(5), eq(0.6d), eq(5),
                eq(false)))
                .thenReturn(new RagChatRetrievalService.RetrievalResult(
                        List.of(new RagSearchResult("doc-1", "관련 없음", Map.of("chunkId", "chunk-1"), 0.7d)),
                        RagChatRetrievalService.RetrievalDebug.disabled()))
                .thenReturn(new RagChatRetrievalService.RetrievalResult(
                        List.of(new RagSearchResult("doc-2", "여름 휴가 규정", Map.of("chunkId", "chunk-2"), 0.9d)),
                        RagChatRetrievalService.RetrievalDebug.disabled()));
        RagRetrievalEvaluationStore store = new InMemoryRagRetrievalEvaluationStore();
        RagRetrievalEvaluationRunner runner = new RagRetrievalEvaluationRunner(retrievalService, store);
        InMemoryRagRetrievalEvaluationQuestionSetStore questionSetStore =
                new InMemoryRagRetrievalEvaluationQuestionSetStore();
        RagRetrievalEvaluationController controller = new RagRetrievalEvaluationController(
                runner,
                store,
                new RagRetrievalEvaluationJobService(
                        runner,
                        Runnable::run,
                        new InMemoryRagRetrievalEvaluationJobStore()),
                questionSetStore,
                new RagRetrievalRecommendationService());

        String beforeRunId = controller.evaluate(request()).getBody().getData().runId();
        String afterRunId = controller.evaluate(request()).getBody().getData().runId();

        var compared = controller.compare(new RagRetrievalEvaluationCompareRequestDto(beforeRunId, afterRunId))
                .getBody()
                .getData();

        assertThat(compared.beforeRunId()).isEqualTo(beforeRunId);
        assertThat(compared.afterRunId()).isEqualTo(afterRunId);
        assertThat(compared.strategies()).singleElement()
                .satisfies(delta -> assertThat(delta.hitRateDelta()).isEqualTo(1.0d));
    }

    @Test
    void evaluationJobRunsAsynchronouslyAndExposesProgress() {
        RagChatRetrievalService retrievalService = mock(RagChatRetrievalService.class);
        when(retrievalService.retrieve(any(), eq("휴가 규정"), eq("attachment"), eq("1"), eq(5), eq(0.6d), eq(5),
                eq(false)))
                .thenReturn(new RagChatRetrievalService.RetrievalResult(
                        List.of(new RagSearchResult("doc-1", "여름 휴가 규정", Map.of("chunkId", "chunk-1"), 0.9d)),
                        RagChatRetrievalService.RetrievalDebug.disabled()));
        RagRetrievalEvaluationStore store = new InMemoryRagRetrievalEvaluationStore();
        RagRetrievalEvaluationRunner runner = new RagRetrievalEvaluationRunner(retrievalService, store);
        RagRetrievalEvaluationJobService jobService = new RagRetrievalEvaluationJobService(
                runner,
                Runnable::run,
                new InMemoryRagRetrievalEvaluationJobStore());
        RagRetrievalEvaluationController controller = new RagRetrievalEvaluationController(
                runner,
                store,
                jobService,
                new InMemoryRagRetrievalEvaluationQuestionSetStore(),
                new RagRetrievalRecommendationService());

        var created = controller.createJob(request()).getBody().getData();
        var completed = controller.getJob(created.jobId()).getBody().getData();

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.completedQuestions()).isEqualTo(1);
        assertThat(completed.completedStrategies()).isEqualTo(1);
        assertThat(completed.runId()).startsWith("reval-");
        assertThat(controller.get(completed.runId()).getBody().getData().runId()).isEqualTo(completed.runId());
        assertThat(controller.listJobs().getBody().getData()).extracting("jobId").contains(created.jobId());
    }

    @Test
    void exportCsvReturnsFlattenedEvaluationRows() {
        RagChatRetrievalService retrievalService = mock(RagChatRetrievalService.class);
        when(retrievalService.retrieve(any(), eq("휴가 규정"), eq("attachment"), eq("1"), eq(5), eq(0.6d), eq(5),
                eq(false)))
                .thenReturn(new RagChatRetrievalService.RetrievalResult(
                        List.of(new RagSearchResult("doc-1", "여름 휴가 규정",
                                Map.of("chunkId", "chunk-1", "strategy", "structure-based"), 0.9d)),
                        RagChatRetrievalService.RetrievalDebug.disabled()));
        RagRetrievalEvaluationStore store = new InMemoryRagRetrievalEvaluationStore();
        RagRetrievalEvaluationRunner runner = new RagRetrievalEvaluationRunner(retrievalService, store);
        InMemoryRagRetrievalEvaluationQuestionSetStore questionSetStore =
                new InMemoryRagRetrievalEvaluationQuestionSetStore();
        RagRetrievalEvaluationController controller = new RagRetrievalEvaluationController(
                runner,
                store,
                new RagRetrievalEvaluationJobService(
                        runner,
                        Runnable::run,
                        new InMemoryRagRetrievalEvaluationJobStore()),
                questionSetStore,
                new RagRetrievalRecommendationService());

        String runId = controller.evaluate(request()).getBody().getData().runId();
        String csv = controller.exportCsv(runId).getBody();

        assertThat(csv).contains("runId,strategy,query,hit");
        assertThat(csv).contains(runId);
        assertThat(csv).contains("chunk-1");
        assertThat(csv).contains("structure-based");
    }

    @Test
    void questionSetCanBeSavedAndExecutedAsEvaluationJob() {
        RagChatRetrievalService retrievalService = mock(RagChatRetrievalService.class);
        when(retrievalService.retrieve(any(), eq("휴가 규정"), eq("attachment"), eq("1"), eq(5), eq(0.6d), eq(5),
                eq(false)))
                .thenReturn(new RagChatRetrievalService.RetrievalResult(
                        List.of(new RagSearchResult("doc-1", "여름 휴가 규정", Map.of("chunkId", "chunk-1"), 0.9d)),
                        RagChatRetrievalService.RetrievalDebug.disabled()));
        RagRetrievalEvaluationStore store = new InMemoryRagRetrievalEvaluationStore();
        RagRetrievalEvaluationRunner runner = new RagRetrievalEvaluationRunner(retrievalService, store);
        InMemoryRagRetrievalEvaluationQuestionSetStore questionSetStore =
                new InMemoryRagRetrievalEvaluationQuestionSetStore();
        RagRetrievalEvaluationController controller = new RagRetrievalEvaluationController(
                runner,
                store,
                new RagRetrievalEvaluationJobService(
                        runner,
                        Runnable::run,
                        new InMemoryRagRetrievalEvaluationJobStore()),
                questionSetStore,
                new RagRetrievalRecommendationService());

        var questionSet = controller.createQuestionSet(new RagRetrievalEvaluationQuestionSetRequestDto(
                "취업규칙 평가",
                "structure/ideaBlock/hybrid 비교용",
                request().questions())).getBody().getData();
        var job = controller.createJobFromQuestionSet(
                questionSet.questionSetId(),
                new RagRetrievalEvaluationQuestionSetRunRequestDto(
                        List.of("hybrid"),
                        5,
                        0.6d,
                        "attachment",
                        "1",
                        "retrieval-ko-kure",
                        null,
                        null,
                        null)).getBody().getData();

        assertThat(questionSet.questionSetId()).startsWith("reqs-");
        assertThat(controller.getQuestionSet(questionSet.questionSetId()).getBody().getData().name())
                .isEqualTo("취업규칙 평가");
        assertThat(controller.listQuestionSets().getBody().getData()).extracting("questionSetId")
                .contains(questionSet.questionSetId());
        var completed = controller.getJob(job.jobId()).getBody().getData();
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(controller.get(completed.runId()).getBody().getData().questionSetId())
                .isEqualTo(questionSet.questionSetId());
        assertThat(controller.listRunsByQuestionSet(questionSet.questionSetId()).getBody().getData())
                .extracting("runId")
                .contains(completed.runId());
        var recommendation = controller.recommendByQuestionSet(questionSet.questionSetId()).getBody().getData();
        assertThat(recommendation.recommendedStrategy()).isEqualTo("hybrid");
        assertThat(recommendation.strategies()).singleElement()
                .satisfies(strategy -> {
                    assertThat(strategy.strategy()).isEqualTo("hybrid");
                    assertThat(strategy.runIds()).contains(completed.runId());
                    assertThat(strategy.hitRate()).isEqualTo(1.0d);
                });
        var analysis = controller.analyzeByQuestionSet(questionSet.questionSetId()).getBody().getData();
        assertThat(analysis.runCount()).isEqualTo(1);
        assertThat(analysis.questionCount()).isEqualTo(1);
        assertThat(analysis.strategies()).singleElement()
                .satisfies(strategy -> {
                    assertThat(strategy.strategy()).isEqualTo("hybrid");
                    assertThat(strategy.failedQuestionCount()).isZero();
                });
        assertThat(analysis.questions()).singleElement()
                .satisfies(question -> {
                    assertThat(question.query()).isEqualTo("휴가 규정");
                    assertThat(question.hitStrategies()).contains("hybrid");
                    assertThat(question.missedStrategies()).isEmpty();
                    assertThat(question.bestStrategy()).isEqualTo("hybrid");
                });

        InMemoryRagRetrievalPolicyStore policyStore = new InMemoryRagRetrievalPolicyStore();
        InMemoryRagRetrievalPolicyUsageStore usageStore = new InMemoryRagRetrievalPolicyUsageStore();
        InMemoryRagRetrievalPolicyHistoryStore historyStore = new InMemoryRagRetrievalPolicyHistoryStore();
        RagRetrievalPolicyController policyController = new RagRetrievalPolicyController(
                policyStore,
                store,
                questionSetStore,
                new RagRetrievalRecommendationService(),
                new studio.one.platform.ai.autoconfigure.AiWebRagProperties.RetrievalProperties(),
                usageStore,
                historyStore);
        var policy = policyController.applyRecommendation(new RagRetrievalPolicyApplyRecommendationRequestDto(
                questionSet.questionSetId(),
                "attachment",
                "1")).getBody().getData();

        assertThat(policy.retrievalStrategy()).isEqualTo("hybrid");
        assertThat(policy.questionSetId()).isEqualTo(questionSet.questionSetId());
        assertThat(policy.retrievalOptions()).isNotNull();
        assertThat(policy.retrievalOptions().structureTopK()).isEqualTo(5);
        assertThat(policy.retrievalOptions().ideaBlockTopK()).isEqualTo(5);
        assertThat(policy.retrievalOptions().finalTopK()).isEqualTo(5);
        assertThat(policyStore.find("attachment", "1")).hasValueSatisfying(saved ->
                assertThat(saved.retrievalStrategy()).isEqualTo("hybrid"));
        assertThat(policyController.history("attachment", "1").getBody().getData()).singleElement()
                .satisfies(history -> {
                    assertThat(history.retrievalStrategy()).isEqualTo("hybrid");
                    assertThat(history.reason()).isEqualTo("APPLY_RECOMMENDATION");
                    assertThat(history.questionSetId()).isEqualTo(questionSet.questionSetId());
                });

        usageStore.save(new studio.one.platform.ai.web.dto.RagRetrievalPolicyUsageDto(
                "rpu-1",
                "attachment",
                "1",
                "hybrid",
                questionSet.questionSetId(),
                completed.runId(),
                5,
                0.6d,
                4,
                false,
                42L,
                Instant.now()));
        assertThat(policyController.usage("attachment", "1").getBody().getData()).singleElement()
                .satisfies(usage -> {
                    assertThat(usage.retrievalStrategy()).isEqualTo("hybrid");
                    assertThat(usage.resultCount()).isEqualTo(4);
                });
        assertThat(policyController.usageSummary("attachment", "1").getBody().getData())
                .satisfies(summary -> {
                    assertThat(summary.usageCount()).isEqualTo(1);
                    assertThat(summary.latestStrategy()).isEqualTo("hybrid");
                });
        assertThatThrownBy(() -> policyController.applyRecommendation(new RagRetrievalPolicyApplyRecommendationRequestDto(
                "reqs-missing",
                "attachment",
                "1")))
                .isInstanceOf(RagRetrievalPolicyException.class)
                .satisfies(ex -> assertThat(((RagRetrievalPolicyException) ex).code())
                        .isEqualTo("RETRIEVAL_QUESTION_SET_NOT_FOUND"));
    }

    @Test
    void benchmarkRunsQuestionSetAcrossObjectsAndReturnsReport() {
        RagChatRetrievalService retrievalService = mock(RagChatRetrievalService.class);
        when(retrievalService.retrieve(any(), eq("휴가 규정"), any(), any(), eq(5), eq(0.6d), eq(5),
                eq(false)))
                .thenAnswer(invocation -> new RagChatRetrievalService.RetrievalResult(
                        List.of(new RagSearchResult("doc-" + invocation.getArgument(3), "여름 휴가 규정",
                                Map.of("chunkId", "chunk-" + invocation.getArgument(3)), 0.9d)),
                        RagChatRetrievalService.RetrievalDebug.disabled()));
        RagRetrievalEvaluationStore store = new InMemoryRagRetrievalEvaluationStore();
        RagRetrievalEvaluationRunner runner = new RagRetrievalEvaluationRunner(retrievalService, store);
        InMemoryRagRetrievalEvaluationQuestionSetStore questionSetStore =
                new InMemoryRagRetrievalEvaluationQuestionSetStore();
        RagRetrievalEvaluationController controller = new RagRetrievalEvaluationController(
                runner,
                store,
                new RagRetrievalEvaluationJobService(
                        runner,
                        Runnable::run,
                        new InMemoryRagRetrievalEvaluationJobStore()),
                questionSetStore,
                new RagRetrievalRecommendationService());
        var questionSet = controller.createQuestionSet(new RagRetrievalEvaluationQuestionSetRequestDto(
                "문서군 평가",
                "반복 benchmark",
                request().questions())).getBody().getData();

        var report = controller.runBenchmark(questionSet.questionSetId(), new RagRetrievalEvaluationBenchmarkRequestDto(
                List.of(
                        new RagRetrievalEvaluationBenchmarkRequestDto.ObjectScope("attachment", "1", "structure"),
                        new RagRetrievalEvaluationBenchmarkRequestDto.ObjectScope("attachment", "2", "blockify")),
                List.of("hybrid"),
                2,
                5,
                0.6d,
                "retrieval-ko-kure",
                null,
                null,
                null)).getBody().getData();

        assertThat(report.questionSetId()).isEqualTo(questionSet.questionSetId());
        assertThat(report.objectCount()).isEqualTo(2);
        assertThat(report.runCount()).isEqualTo(4);
        assertThat(report.aggregateRecommendation().recommendedStrategy()).isEqualTo("hybrid");
        assertThat(report.aggregateAnalysis().runCount()).isEqualTo(4);
        assertThat(report.objects()).hasSize(2);
        assertThat(report.objects()).allSatisfy(object -> {
            assertThat(object.runCount()).isEqualTo(2);
            assertThat(object.runIds()).hasSize(2);
            assertThat(object.recommendation().recommendedStrategy()).isEqualTo("hybrid");
            assertThat(object.analysis().runCount()).isEqualTo(2);
        });
        assertThat(controller.listRunsByQuestionSet(questionSet.questionSetId()).getBody().getData())
                .hasSize(4);
    }

    private RagRetrievalEvaluationRequestDto request() {
        return new RagRetrievalEvaluationRequestDto(
                List.of("hybrid"),
                List.of(new RagRetrievalEvaluationRequestDto.Question(
                        "휴가 규정",
                        null,
                        null,
                        List.of("휴가"))),
                5,
                0.6d,
                "attachment",
                "1",
                null,
                "retrieval-ko-kure",
                null,
                null,
                null);
    }
}
