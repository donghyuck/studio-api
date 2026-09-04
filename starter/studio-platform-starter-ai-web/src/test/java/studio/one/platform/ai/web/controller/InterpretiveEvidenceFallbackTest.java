package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagSearchResult;

class InterpretiveEvidenceFallbackTest {

    @Test
    void createsABoundedCitationValidDigestFromVerifiedEvidenceOnly() {
        PackedEvidenceSet evidenceSet = PackedEvidenceSet.from(
                "context",
                List.of(
                        result("one", "홀든은 학교에서 소외감을 느낀다.", "SOURCE_VERIFIED"),
                        result("two", "홀든은 동생 피비를 보호하려 한다.", "SOURCE_VERIFIED"),
                        result("three", "검증되지 않은 추정입니다.", "UNVERIFIED")),
                Map.of());

        String draft = new InterpretiveEvidenceFallback().draft(evidenceSet).orElseThrow();

        assertThat(draft)
                .contains("홀든은 학교에서 소외감을 느낀다.\" [1]")
                .contains("홀든은 동생 피비를 보호하려 한다.\" [2]")
                .contains("확인 한계")
                .doesNotContain("검증되지 않은 추정");
        assertThat(new RagCitationValidator().validate(draft, evidenceSet).valid()).isTrue();
    }

    private RagSearchResult result(String id, String content, String supportStatus) {
        return new RagSearchResult(id, content, Map.of(
                "chunkId", id,
                "supportStatus", supportStatus), 1.0d);
    }
}
