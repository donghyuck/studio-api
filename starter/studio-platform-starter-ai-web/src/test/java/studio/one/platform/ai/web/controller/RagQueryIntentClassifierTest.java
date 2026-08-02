package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RagQueryIntentClassifierTest {

    private final RagQueryIntentClassifier classifier = RagQueryIntentClassifier.rules();

    @Test
    void classifiesEvaluativeQuestionAsInterpretiveAnalysis() {
        assertThat(classifier.classify("트럼프는 전쟁광인가").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.INTERPRETIVE_ANALYSIS);
        assertThat(classifier.classify("트럼프를 전쟁광으로 볼 수 있는가").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.INTERPRETIVE_ANALYSIS);
    }

    @Test
    void classifiesScholarAndPersonLookupAsFactualList() {
        assertThat(classifier.classify("진균과 연관된 학자는").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.FACTUAL_LIST);
        assertThat(classifier.classify("문서에 언급된 연구자는 누구들이야?").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.FACTUAL_LIST);
        assertThat(classifier.classify("진균과 관련있는 과학자는").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.FACTUAL_LIST);
        assertThat(classifier.classify("진균과 관련 있는 연구자를 알려줘").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.FACTUAL_LIST);
        assertThat(classifier.classify("진균과 관련있는 수학자는").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.FACTUAL_LIST);
        assertThat(classifier.classify("진균 관련 수학자는").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.FACTUAL_LIST);
        assertThat(classifier.classify("문서와 연관된 철학자를 알려줘").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.FACTUAL_LIST);
        assertThat(classifier.classify("이 사건에 언급된 기관은 무엇인가").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.FACTUAL_LIST);
        assertThat(classifier.classify("related scholars").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.FACTUAL_LIST);
    }

    @Test
    void preservesMetadataAndInterpretivePrecedenceOverFactualList() {
        assertThat(classifier.classify("이 책의 저자가 누구인가").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.DOCUMENT_METADATA);
        assertThat(classifier.classify("트럼프는 전쟁광인가").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.INTERPRETIVE_ANALYSIS);
        assertThat(classifier.classify("진균과 관련있는 수학자는 유능한가").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.INTERPRETIVE_ANALYSIS);
        assertThat(classifier.classify("진균과 관련있는 내용을 요약해줘").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.DOCUMENT_SUMMARY);
        assertThat(classifier.classify("진균과 관련있는 수학자의 생애는").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.CONTENT_QA);
    }

    @Test
    void classifiesDocumentSummaryQueries() {
        assertThat(classifier.classify("이 문서를 요약해줘").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.DOCUMENT_SUMMARY);
    }

    @Test
    void classifiesObjectMetadataQueriesWithoutCapturingAuthorArgumentQuestions() {
        assertThat(classifier.classify("이 책의 저자는 누구인가?").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.DOCUMENT_METADATA);
        assertThat(classifier.classify("작가는 누구인가").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.DOCUMENT_METADATA);
        assertThat(classifier.classify("ISBN과 발간일을 알려줘").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.DOCUMENT_METADATA);
        assertThat(classifier.classify("저자의 핵심 논지를 설명해줘").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.KEY_POINTS);
    }

    @Test
    void classifiesKeyPointQueriesBeforeGenericSummaryPhrases() {
        assertThat(classifier.classify("핵심 내용을 요약해줘").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.KEY_POINTS);
        assertThat(classifier.classify("이 책의 핵심 주제는 무엇인가?").intent())
                .isEqualTo(RagQueryIntentClassifier.Intent.KEY_POINTS);
        assertThat(classifier.classify("저자의 핵심 논지를 근거와 함께 설명해줘").intent())
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
