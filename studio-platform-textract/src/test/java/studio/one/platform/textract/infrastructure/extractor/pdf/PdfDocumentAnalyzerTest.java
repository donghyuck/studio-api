package studio.one.platform.textract.infrastructure.extractor.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PdfDocumentAnalyzerTest {

    private final PdfDocumentAnalyzer analyzer = new PdfDocumentAnalyzer();

    @Test
    void classifiesMathLikeTextbookSignals() {
        PdfDocumentAnalysis analysis = analyzer.analyzeText(
                10,
                2,
                """
                        이차방정식 x^2 + 3x + 2 = 0 의 해를 구하시오.
                        함수 f(x)=sin x + cos x 를 미분하시오.
                        """,
                "고등수학.pdf");

        assertThat(analysis.documentKind()).isIn(PdfDocumentKind.MATH_LIKE, PdfDocumentKind.MIXED);
        assertThat(analysis.mathSignalScore()).isGreaterThan(0.0d);
        assertThat(analysis.mathLike()).isTrue();
    }

    @Test
    void classifiesDecomposedKoreanMathFilename() {
        PdfDocumentAnalysis analysis = analyzer.analyzeText(
                44,
                5,
                "",
                "미래엔_고등_수학.pdf");

        assertThat(analysis.documentKind()).isEqualTo(PdfDocumentKind.MIXED);
        assertThat(analysis.mathLike()).isTrue();
        assertThat(analysis.ocrRecommended()).isTrue();
    }

    @Test
    void recommendsOcrWhenSampleTextIsAlmostEmpty() {
        PdfDocumentAnalysis analysis = analyzer.analyzeText(20, 3, "  \n", "scan.pdf");

        assertThat(analysis.documentKind()).isEqualTo(PdfDocumentKind.SCANNED);
        assertThat(analysis.ocrRecommended()).isTrue();
    }

    @Test
    void treatsHighMathSignalAsMathLikeEvenWhenKindIsScannedOrGeneral() {
        PdfDocumentAnalysis scanned = new PdfDocumentAnalysis(
                44, 5, 0.0d, 1.0d, 0.0d, true, 0.0d, 0.0d, 1.0d, PdfDocumentKind.SCANNED);
        PdfDocumentAnalysis general = new PdfDocumentAnalysis(
                44, 5, 0.3d, 0.1d, 0.0d, false, 0.0d, 0.0d, 0.5d, PdfDocumentKind.GENERAL);

        assertThat(scanned.mathLike()).isTrue();
        assertThat(general.mathLike()).isTrue();
    }
}
