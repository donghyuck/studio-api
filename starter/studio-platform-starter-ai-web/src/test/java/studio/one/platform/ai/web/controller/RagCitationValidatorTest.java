package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagSearchResult;

class RagCitationValidatorTest {

    private final RagCitationValidator validator = new RagCitationValidator();

    @Test
    void acceptsOnlyIndexesPresentInPackedEvidence() {
        PackedEvidenceSet evidence = PackedEvidenceSet.from(
                "context",
                List.of(new RagSearchResult("doc", "exact source", Map.of("chunkId", "chunk-1"), 0.9d)),
                Map.of());

        assertThat(validator.validate("사실입니다 [1]", evidence).status())
                .isEqualTo(RagCitationValidator.Status.INDEX_VALID);
        assertThat(validator.validate("잘못된 인용 [999]", evidence).status())
                .isEqualTo(RagCitationValidator.Status.OUT_OF_RANGE);
        assertThat(validator.validate("인용 없음", evidence).status())
                .isEqualTo(RagCitationValidator.Status.MISSING_CITATION);
    }

    @Test
    void ignoresCitationLikeTextInsideCodeFences() {
        PackedEvidenceSet evidence = PackedEvidenceSet.from(
                "context",
                List.of(new RagSearchResult("doc", "exact source", Map.of("chunkId", "chunk-1"), 0.9d)),
                Map.of());

        assertThat(validator.validate("```text\n[999]\n```\n근거 [1]", evidence).status())
                .isEqualTo(RagCitationValidator.Status.INDEX_VALID);
    }

    @Test
    void acceptsGroupedCitationIndexesRequiredByThePrompt() {
        PackedEvidenceSet evidence = PackedEvidenceSet.from(
                "context",
                List.of(
                        new RagSearchResult("doc", "first source", Map.of("chunkId", "chunk-1"), 0.9d),
                        new RagSearchResult("doc", "second source", Map.of("chunkId", "chunk-2"), 0.8d)),
                Map.of());

        RagCitationValidator.Validation validation =
                validator.validate("여러 근거를 종합한 답변입니다 [1, 2]", evidence);

        assertThat(validation.status()).isEqualTo(RagCitationValidator.Status.INDEX_VALID);
        assertThat(validation.citedIndexes()).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void rejectsOutOfRangeIndexInsideGroupedCitation() {
        PackedEvidenceSet evidence = PackedEvidenceSet.from(
                "context",
                List.of(new RagSearchResult("doc", "exact source", Map.of("chunkId", "chunk-1"), 0.9d)),
                Map.of());

        RagCitationValidator.Validation validation =
                validator.validate("일부 번호가 잘못되었습니다 [1, 999]", evidence);

        assertThat(validation.status()).isEqualTo(RagCitationValidator.Status.OUT_OF_RANGE);
        assertThat(validation.citedIndexes()).containsExactly(1);
        assertThat(validation.invalidIndexes()).containsExactly(999);
    }

    @Test
    void treatsOversizedCitationAsMissingInsteadOfFailingValidation() {
        PackedEvidenceSet evidence = PackedEvidenceSet.from(
                "context",
                List.of(new RagSearchResult("doc", "exact source", Map.of("chunkId", "chunk-1"), 0.9d)),
                Map.of());

        assertThat(validator.validate("비정상적으로 큰 번호 [999999999999999999999]", evidence).status())
                .isEqualTo(RagCitationValidator.Status.MISSING_CITATION);
    }
}
