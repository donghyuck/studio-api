package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagSearchResult;

class RagAnswerPolicyValidatorTest {

    private final RagAnswerPolicyValidator validator = new RagAnswerPolicyValidator();
    private final RagCitationValidator citationValidator = new RagCitationValidator();
    private final PackedEvidenceSet evidenceSet = PackedEvidenceSet.from(
            "context",
            List.of(new RagSearchResult("chunk-1", "evidence", Map.of(), 1.0d)),
            Map.of());

    @Test
    void strictModeRequiresCitationForEachSubstantiveParagraph() {
        RagAnswerPolicyValidator.Validation validation = validator.validate(
                "첫 번째 주요 문단에는 충분히 긴 설명과 근거가 있습니다. [1]\n\n"
                        + "두 번째 주요 문단에는 충분히 긴 설명이 있지만 근거 번호가 없습니다.",
                evidenceSet,
                policy(RagAnswerMode.STRICT_GROUNDED),
                citationValidator);

        assertThat(validation.status())
                .isEqualTo(RagAnswerPolicyValidator.Status.MISSING_UNIT_CITATION);
    }

    @Test
    void inferenceModeAlsoRequiresInterpretationParagraphCitation() {
        RagAnswerPolicyValidator.Validation validation = validator.validate(
                "직접 확인되는 사실을 충분히 설명하는 문단입니다. [1]\n\n"
                        + "근거를 종합한 해석이지만 인용 번호가 빠진 충분히 긴 문단입니다.",
                evidenceSet,
                policy(RagAnswerMode.GROUNDED_INFERENCE),
                citationValidator);

        assertThat(validation.status())
                .isEqualTo(RagAnswerPolicyValidator.Status.MISSING_UNIT_CITATION);
    }

    @Test
    void strictUsesSentenceUnitsWhileInferenceUsesParagraphUnits() {
        String content = "문서에서 직접 확인되는 첫 번째 사실입니다. [1] "
                + "같은 문단의 두 번째 실질 문장에는 별도 인용이 없습니다.";

        assertThat(validator.validate(
                content, evidenceSet, policy(RagAnswerMode.STRICT_GROUNDED), citationValidator).status())
                .isEqualTo(RagAnswerPolicyValidator.Status.MISSING_UNIT_CITATION);
        assertThat(validator.validate(
                content, evidenceSet, policy(RagAnswerMode.GROUNDED_INFERENCE), citationValidator).status())
                .isEqualTo(RagAnswerPolicyValidator.Status.STRUCTURE_VALID);
    }

    @Test
    void numberedAndBulletedListItemsUseTheSameCitationRuleInBothModes() {
        String content = "1. 첫 번째 학자는 문서에서 발효 연구와 관련됩니다 [1]\n"
                + "- 두 번째 학자는 문서에 언급되지만 인용이 없습니다";

        RagAnswerPolicyValidator.Validation strict = validator.validate(
                content, evidenceSet, policy(RagAnswerMode.STRICT_GROUNDED), citationValidator);
        RagAnswerPolicyValidator.Validation inference = validator.validate(
                content, evidenceSet, policy(RagAnswerMode.GROUNDED_INFERENCE), citationValidator);

        assertThat(strict.status()).isEqualTo(RagAnswerPolicyValidator.Status.MISSING_UNIT_CITATION);
        assertThat(inference.status()).isEqualTo(RagAnswerPolicyValidator.Status.MISSING_UNIT_CITATION);
        assertThat(strict.units()).extracting(RagAnswerPolicyValidator.ValidationUnit::kind)
                .containsOnly(RagAnswerPolicyValidator.UnitKind.LIST_ITEM);
        assertThat(strict.unitCount()).isEqualTo(2);
        assertThat(strict.citedUnitCount()).isEqualTo(1);
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
}
