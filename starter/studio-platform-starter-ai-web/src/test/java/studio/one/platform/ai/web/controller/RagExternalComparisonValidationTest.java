package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.rag.external.ExternalEvidence;
import studio.one.platform.ai.core.rag.external.ExternalEvidenceSourceType;

class RagExternalComparisonValidationTest {

    private final RagAnswerPolicyValidator validator = new RagAnswerPolicyValidator();
    private final RagCitationValidator citationValidator = new RagCitationValidator();
    private final ResolvedRagAnswerPolicy answerPolicy =
            RagAnswerPolicyResolver.defaults().resolve(null);

    @Test
    void packsDocumentAndExternalEvidenceWithDistinctPublicOrigins() {
        PackedEvidenceSet evidenceSet = evidenceSet();

        assertThat(evidenceSet.evidence()).extracting(PackedEvidenceSet.PackedEvidence::origin)
                .containsExactly("DOCUMENT", "OFFICIAL_EXTERNAL");
        assertThat(evidenceSet.promptContext()).contains("<EXTERNAL_EVIDENCE>", "[근거 2]");

        List<Map<String, Object>> references =
                evidenceSet.toPublicReferences(Set.of(1, 2), "CITED");
        assertThat(references).extracting(reference -> reference.get("origin"))
                .containsExactly("DOCUMENT", "OFFICIAL_EXTERNAL");
        assertThat(references.get(1))
                .containsEntry("sourceType", "STATUTE")
                .containsEntry("canonicalUrl", "https://law.example.test/rule");
    }

    @Test
    void comparisonRequiresCitationsFromDocumentAndExternalEvidence() {
        RagAnswerPolicyValidator.Validation valid = validator.validate(
                "문서 내용은 근태 위반을 징계 사유로 규정합니다. [1]\n\n"
                        + "외부 법령은 정당한 이유를 요구합니다. [2]\n\n"
                        + "두 기준은 조건부로 함께 검토해야 합니다. [1, 2]",
                evidenceSet(),
                answerPolicy,
                citationValidator);

        assertThat(valid.status()).isEqualTo(RagAnswerPolicyValidator.Status.STRUCTURE_VALID);

        RagAnswerPolicyValidator.Validation missingExternal = validator.validate(
                "문서 규정만으로 외부 기준까지 판단할 수 있습니다. [1]",
                evidenceSet(),
                answerPolicy,
                citationValidator);

        assertThat(missingExternal.status())
                .isEqualTo(RagAnswerPolicyValidator.Status.MISSING_COMPARISON_SOURCE_CITATION);
    }

    private PackedEvidenceSet evidenceSet() {
        RagSearchResult document = new RagSearchResult(
                "document-1",
                "근태 위반은 징계 사유가 될 수 있다.",
                Map.of("chunkId", "chunk-1", "supportStatus", "SOURCE_VERIFIED"),
                0.8d);
        PackedEvidenceSet base = PackedEvidenceSet.from(
                "[근거 1]\n근태 위반은 징계 사유가 될 수 있다.",
                List.of(document),
                Map.of());
        ExternalEvidence external = new ExternalEvidence(
                "external-1",
                ExternalEvidenceSourceType.STATUTE,
                "Official rule",
                "Official publisher",
                URI.create("https://law.example.test/rule"),
                null,
                null,
                Instant.parse("2026-07-28T00:00:00Z"),
                "사용자는 정당한 이유 없이 근로자를 해고하지 못한다.",
                "hash-1",
                1.0d,
                Map.of());
        return base.withExternalEvidence(List.of(external))
                .withDiagnostic(
                        "coverageRequirement",
                        RagEvidenceCoverageRequirement.DOCUMENT_AND_EXTERNAL.name());
    }
}
