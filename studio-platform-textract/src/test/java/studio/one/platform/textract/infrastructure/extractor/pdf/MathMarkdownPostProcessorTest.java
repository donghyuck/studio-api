package studio.one.platform.textract.infrastructure.extractor.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.textract.domain.model.BlockType;
import studio.one.platform.textract.domain.model.DocumentFormat;
import studio.one.platform.textract.domain.model.ParsedBlock;
import studio.one.platform.textract.domain.model.ParsedFile;

class MathMarkdownPostProcessorTest {

    @Test
    void wrapsEquationSpansAsMarkdownMath() {
        ParsedFile file = new ParsedFile(
                DocumentFormat.PDF,
                "예를 들어 2x^2+3x-4=0 을 계산한다.",
                List.of(ParsedBlock.text("b1", BlockType.OCR_TEXT,
                        "예를 들어 2x^2+3x-4=0 을 계산한다.", 1, 0, Map.of())),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true);

        ParsedFile result = new MathMarkdownPostProcessor().process(file);

        assertThat(result.markdown()).contains("$2x^{2}+3x-4=0$");
        assertThat(result.blocks().get(0).text()).contains("$2x^{2}+3x-4=0$");
        assertThat(result.metadata())
                .containsEntry(MathMarkdownPostProcessor.KEY_MATH_MARKDOWN_APPLIED, true)
                .containsEntry(MathMarkdownPostProcessor.KEY_MATH_MARKDOWN_ENGINE, "heuristic-v1")
                .containsEntry(MathMarkdownPostProcessor.KEY_MATH_MARKDOWN_QUALITY, "REVIEW_REQUIRED")
                .containsEntry(MathMarkdownPostProcessor.KEY_MATH_DOCUMENT_ENGINE_REQUIRED, true);
    }

    @Test
    void keepsExistingMarkdownMath() {
        ParsedFile file = ParsedFile.textOnly(DocumentFormat.PDF, "$x^2+1=0$", "math.pdf");

        ParsedFile result = new MathMarkdownPostProcessor().process(file);

        assertThat(result.markdown()).isEqualTo("$x^2+1=0$");
        assertThat(result.metadata())
                .containsEntry(MathMarkdownPostProcessor.KEY_MATH_MARKDOWN_APPLIED, false)
                .containsEntry(MathMarkdownPostProcessor.KEY_MATH_MARKDOWN_EXPRESSION_COUNT, 0);
    }

    @Test
    void normalizesCommonOcrMathCharacters() {
        ParsedFile file = ParsedFile.textOnly(DocumentFormat.PDF, "2x°+3x—4=0", "math.pdf");

        ParsedFile result = new MathMarkdownPostProcessor().process(file);

        assertThat(result.markdown()).contains("$2x^{2}+3x-4=0$");
        assertThat(result.metadata())
                .containsEntry(MathMarkdownPostProcessor.KEY_MATH_MARKDOWN_APPLIED, true)
                .containsEntry(MathMarkdownPostProcessor.KEY_MATH_MARKDOWN_EXPRESSION_COUNT, 1);
    }
}
