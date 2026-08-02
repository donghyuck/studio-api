package studio.one.platform.ai.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RagEvidenceCoverageRequirementTest {

    @Test
    void detectsComparisonByRequestedActionRatherThanTopicNoun() {
        assertEquals(
                RagEvidenceCoverageRequirement.DOCUMENT_AND_EXTERNAL,
                RagEvidenceCoverageRequirement.classify("첨부 문서와 외부 자료를 비교하여 검토해줘"));
        assertEquals(
                RagEvidenceCoverageRequirement.DOCUMENT_AND_EXTERNAL,
                RagEvidenceCoverageRequirement.classify("외부 법규 기준으로 문서 내용을 확인해줘"));
        assertEquals(
                RagEvidenceCoverageRequirement.ANY_RELEVANT_SOURCE,
                RagEvidenceCoverageRequirement.classify("진균과 관련된 수학자는 누구인가"));
    }
}
