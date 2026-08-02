package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.rag.external.ExternalEvidence;
import studio.one.platform.ai.core.rag.external.ExternalEvidenceSourceType;

class PackedEvidenceSetTest {

    @Test
    void publicExcerptCentersOnMatchedQueryTermWithoutChangingSourceText() {
        String sourceText = "앞 문맥 ".repeat(120)
                + "트럼프 전쟁광 관련 핵심 원문"
                + " 뒤 문맥".repeat(120);
        Map<String, Object> span = Map.of(
                "exactText", sourceText,
                "chunkId", "chunk-49",
                "startOffset", 0,
                "endOffset", sourceText.length(),
                "truncated", false,
                "_matchedQueryTerms", List.of("트럼프", "전쟁광"));
        RagSearchResult result = new RagSearchResult(
                "document-11",
                sourceText,
                Map.of("sourceSpans", List.of(span)),
                1.0d);

        PackedEvidenceSet evidenceSet = PackedEvidenceSet.from("context", List.of(result), Map.of());
        String excerpt = evidenceSet.toPublicReferences(Set.of(1), "CITED").get(0).get("exactText").toString();

        assertThat(excerpt)
                .hasSizeLessThanOrEqualTo(500)
                .contains("트럼프 전쟁광 관련 핵심 원문");
        assertThat(sourceText).contains(excerpt);
    }

    @Test
    void externalEvidenceRespectsCombinedPromptBudgetAndExactExcerptContract() {
        String original = "공식 원문 ".repeat(2_000);
        ExternalEvidence evidence = new ExternalEvidence(
                "external-1",
                ExternalEvidenceSourceType.STATUTE,
                "근로기준법",
                "국가법령정보센터",
                URI.create("https://law.go.kr/example"),
                null,
                null,
                Instant.parse("2026-07-28T00:00:00Z"),
                original,
                "hash",
                1.0d,
                Map.of());

        PackedEvidenceSet evidenceSet = PackedEvidenceSet
                .empty("문서 근거", Map.of())
                .withExternalEvidence(List.of(evidence));

        assertThat(evidenceSet.promptContext()).hasSizeLessThanOrEqualTo(24_000);
        assertThat(evidenceSet.evidence()).hasSize(1);
        String packedExactText = evidenceSet.evidence().get(0).sourceSpans().get(0).exactText();
        assertThat(packedExactText)
                .hasSizeLessThanOrEqualTo(2_000)
                .isSubstringOf(original);
        assertThat(evidenceSet.promptContext()).contains(packedExactText);
    }
}
