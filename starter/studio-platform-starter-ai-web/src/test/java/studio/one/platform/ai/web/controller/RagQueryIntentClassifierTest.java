package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RagQueryIntentClassifierTest {

    private final RagQueryIntentClassifier classifier = RagQueryIntentClassifier.rules();

    @Test
    void classifiesDocumentSummaryQueries() {
        assertThat(classifier.classify("이 문서를 요약해줘").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.DOCUMENT_SUMMARY);
    }

    @Test
    void classifiesKeyPointQueriesBeforeGenericSummaryPhrases() {
        assertThat(classifier.classify("핵심 내용을 요약해줘").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.KEY_POINTS);
    }

    @Test
    void treatsSpecificQuestionsAsContentQa() {
        assertThat(classifier.classify("A-3B를 구하시오").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.CONTENT_QA);
    }

    @Test
    void classifiesEvidenceBasedCharacterInterpretationQueries() {
        assertThat(classifier.classify("주인공의 MBTI 성격 유형을 문서 근거로 추정해줘").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.INTERPRETIVE_ANALYSIS);
        assertThat(classifier.classify("주인공의 성격은 어떤가?").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.INTERPRETIVE_ANALYSIS);
        assertThat(classifier.classify("Analyze the character motivation").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.INTERPRETIVE_ANALYSIS);
    }
}
