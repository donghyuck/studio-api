package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagSearchResult;

class RagAnswerFinalizerTest {

    private final RagAnswerFinalizer finalizer = new RagAnswerFinalizer();
    private final PackedEvidenceSet evidenceSet = PackedEvidenceSet.from(
            "context",
            List.of(
                    result("chunk-1", "트럼프는 자신을 전쟁을 끝내는 지도자로 묘사했다.", 0.63d),
                    result("chunk-2", "저자는 그 주장과 실제 군사 정책 사이의 긴장을 비판한다.", 0.59d),
                    result("chunk-3", "세 번째 검증된 원문 구간입니다.", 0.55d),
                    result("chunk-4", "네 번째 원문은 후보 제한으로 제외됩니다.", 0.50d)),
            Map.of());

    @Test
    void returnsEvidenceOnlyInsteadOfNoResultsWhenDraftHasNoCitation() {
        RagAnswerFinalizer.FinalizedAnswer answer = finalizer.finalizeAnswer(
                "문서는 트럼프의 자기 묘사와 실제 정책을 구분해 평가합니다.",
                evidenceSet,
                policy(RagAnswerMode.GROUNDED_INFERENCE));

        assertThat(answer.outcome().type()).isEqualTo(RagAnswerOutcome.Type.EVIDENCE_ONLY);
        assertThat(answer.outcome().reasonCode()).isEqualTo(RagAnswerOutcome.ReasonCode.MISSING_CITATION);
        assertThat(answer.outcome().usedEvidenceIndexes()).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(answer.canonicalContent()).isEqualTo(RagAnswerFinalizer.CITATION_VALIDATION_FAILED_MESSAGE);
    }

    @Test
    void normalizesSupportedCitationSyntaxWithoutInventingIndexes() {
        RagAnswerFinalizer.FinalizedAnswer answer = finalizer.finalizeAnswer(
                "문서에서 직접 확인되는 충분히 긴 사실입니다. 【1】",
                evidenceSet,
                policy(RagAnswerMode.GROUNDED_INFERENCE));

        assertThat(answer.outcome().type()).isEqualTo(RagAnswerOutcome.Type.ANSWERED);
        assertThat(answer.canonicalContent()).contains("[1]").doesNotContain("【1】");
    }

    @Test
    void attachesStandaloneCitationLineToPreviousParagraphWithoutInventingIndexes() {
        RagAnswerFinalizer.FinalizedAnswer answer = finalizer.finalizeAnswer(
                "포도소프트웨어는 교육 플랫폼과 관련 솔루션을 제공하는 회사로 소개됩니다.\n\n[1, 2]",
                evidenceSet,
                policy(RagAnswerMode.STRICT_GROUNDED));

        assertThat(answer.outcome().type()).isEqualTo(RagAnswerOutcome.Type.ANSWERED);
        assertThat(answer.canonicalContent())
                .isEqualTo("포도소프트웨어는 교육 플랫폼과 관련 솔루션을 제공하는 회사로 소개됩니다. [1, 2]\n");
        assertThat(answer.outcome().usedEvidenceIndexes()).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void standaloneOutOfRangeCitationRemainsInvalid() {
        RagAnswerFinalizer.FinalizedAnswer answer = finalizer.finalizeAnswer(
                "포도소프트웨어에 대한 충분히 긴 설명입니다.\n[999]",
                evidenceSet,
                policy(RagAnswerMode.STRICT_GROUNDED));

        assertThat(answer.outcome().type()).isEqualTo(RagAnswerOutcome.Type.EVIDENCE_ONLY);
        assertThat(answer.outcome().reasonCode()).isEqualTo(RagAnswerOutcome.ReasonCode.OUT_OF_RANGE_CITATION);
    }

    @Test
    void doesNotMoveStandaloneCitationOutOfCodeFence() {
        RagAnswerFinalizer.FinalizedAnswer answer = finalizer.finalizeAnswer(
                "```text\n포도소프트웨어에 대한 충분히 긴 설명입니다.\n[1]\n```",
                evidenceSet,
                policy(RagAnswerMode.STRICT_GROUNDED));

        assertThat(answer.outcome().type()).isEqualTo(RagAnswerOutcome.Type.EVIDENCE_ONLY);
        assertThat(answer.outcome().reasonCode()).isEqualTo(RagAnswerOutcome.ReasonCode.MISSING_CITATION);
    }

    @Test
    void publicCandidateReferencesAreBoundedAndHideInternalIdentifiers() {
        var references = evidenceSet.toPublicReferences(
                java.util.Set.of(1, 2, 3, 4), "RETRIEVED_ONLY");

        assertThat(references).hasSize(3);
        assertThat(references).allSatisfy(reference -> {
            assertThat(reference.get("usageStatus")).isEqualTo("RETRIEVED_ONLY");
            assertThat(reference.get("exactText").toString()).hasSizeLessThanOrEqualTo(500);
            assertThat(reference).doesNotContainKeys(
                    "documentId", "revisionId", "chunkId", "sourceRef", "startOffset", "endOffset");
        });
    }

    @Test
    void retainsOnlyCitedItemsForFactualListWhenFeatureIsEnabled() {
        RagAnswerFinalizer enabled = new RagAnswerFinalizer(
                new RagCitationValidator(),
                new RagAnswerPolicyValidator(),
                true);

        RagAnswerFinalizer.FinalizedAnswer answer = enabled.finalizeAnswer(
                "- 루이 파스퇴르 — 효모 발효 연구와 관련된 학자로 설명됩니다 [1]\n"
                        + "- 인용 없는 학자 — 문서와 관련된 것으로 생성됐지만 근거 번호가 없습니다\n"
                        + "- 두 번째 학자 — 진균 연구에 대한 비판적 관점을 제시합니다 [2]",
                evidenceSet,
                policy(RagAnswerMode.STRICT_GROUNDED),
                factualList());

        assertThat(answer.outcome().type()).isEqualTo(RagAnswerOutcome.Type.ANSWERED);
        assertThat(answer.outcome().partial()).isTrue();
        assertThat(answer.outcome().originalValidationUnitCount()).isEqualTo(3);
        assertThat(answer.outcome().omittedValidationUnitCount()).isEqualTo(1);
        assertThat(answer.outcome().usedEvidenceIndexes()).containsExactlyInAnyOrder(1, 2);
        assertThat(answer.canonicalContent())
                .contains("루이 파스퇴르", "두 번째 학자")
                .doesNotContain("인용 없는 학자");
    }

    @Test
    void keepsExistingEvidenceOnlyBehaviorWhenFeatureIsDisabled() {
        RagAnswerFinalizer.FinalizedAnswer answer = finalizer.finalizeAnswer(
                "- 루이 파스퇴르 — 효모 발효 연구와 관련된 학자로 설명됩니다 [1]\n"
                        + "- 인용 없는 학자 — 문서와 관련됐지만 근거 번호가 없습니다",
                evidenceSet,
                policy(RagAnswerMode.STRICT_GROUNDED),
                factualList());

        assertThat(answer.outcome().type()).isEqualTo(RagAnswerOutcome.Type.EVIDENCE_ONLY);
        assertThat(answer.outcome().reasonCode()).isEqualTo(RagAnswerOutcome.ReasonCode.MISSING_UNIT_CITATION);
    }

    @Test
    void doesNotPartiallyRetainListWithOutOfRangeCitation() {
        RagAnswerFinalizer enabled = new RagAnswerFinalizer(
                new RagCitationValidator(),
                new RagAnswerPolicyValidator(),
                true);

        RagAnswerFinalizer.FinalizedAnswer answer = enabled.finalizeAnswer(
                "- 루이 파스퇴르 — 효모 발효 연구와 관련된 학자로 설명됩니다 [1]\n"
                        + "- 잘못된 학자 — 제공되지 않은 근거를 참조합니다 [999]",
                evidenceSet,
                policy(RagAnswerMode.STRICT_GROUNDED),
                factualList());

        assertThat(answer.outcome().type()).isEqualTo(RagAnswerOutcome.Type.EVIDENCE_ONLY);
        assertThat(answer.outcome().reasonCode()).isEqualTo(RagAnswerOutcome.ReasonCode.OUT_OF_RANGE_CITATION);
    }

    @Test
    void convertsFactualListNoMatchMarkerToSafeAbstention() {
        RagAnswerFinalizer.FinalizedAnswer answer = finalizer.finalizeAnswer(
                RagAnswerPromptComposer.NO_MATCHING_TARGET_MARKER,
                evidenceSet,
                policy(RagAnswerMode.GROUNDED_INFERENCE),
                factualList());

        assertThat(answer.outcome().type()).isEqualTo(RagAnswerOutcome.Type.ABSTAINED);
        assertThat(answer.outcome().stage()).isEqualTo(RagAnswerOutcome.Stage.GENERATION);
        assertThat(answer.outcome().reasonCode()).isEqualTo(RagAnswerOutcome.ReasonCode.NO_MATCHING_TARGET);
        assertThat(answer.outcome().usedEvidenceIndexes()).isEmpty();
        assertThat(answer.canonicalContent()).isEqualTo(RagAnswerFinalizer.NO_MATCHING_TARGET_MESSAGE);
    }

    @Test
    void doesNotHonorNoMatchMarkerOutsideFactualListIntent() {
        RagAnswerFinalizer.FinalizedAnswer answer = finalizer.finalizeAnswer(
                RagAnswerPromptComposer.NO_MATCHING_TARGET_MARKER,
                evidenceSet,
                policy(RagAnswerMode.GROUNDED_INFERENCE),
                new RagQueryIntentClassifier.Classification(
                        RagQueryIntentClassifier.Intent.CONTENT_QA,
                        1.0d,
                        "TEST"));

        assertThat(answer.outcome().type()).isEqualTo(RagAnswerOutcome.Type.EVIDENCE_ONLY);
        assertThat(answer.outcome().reasonCode()).isEqualTo(RagAnswerOutcome.ReasonCode.MISSING_CITATION);
    }

    private RagSearchResult result(String id, String content, double score) {
        return new RagSearchResult(id, content, Map.of("supportStatus", "SOURCE_VERIFIED"), score);
    }

    private ResolvedRagAnswerPolicy policy(RagAnswerMode mode) {
        return new ResolvedRagAnswerPolicy(
                mode,
                mode,
                ResolvedRagAnswerPolicy.Source.REQUEST,
                false,
                ResolvedRagAnswerPolicy.ReasonCode.NONE,
                "test",
                "fingerprint");
    }

    private RagQueryIntentClassifier.Classification factualList() {
        return new RagQueryIntentClassifier.Classification(
                RagQueryIntentClassifier.Intent.FACTUAL_LIST,
                1.0d,
                "TEST");
    }
}
