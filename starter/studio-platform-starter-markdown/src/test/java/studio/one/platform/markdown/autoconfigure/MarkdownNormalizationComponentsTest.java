package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.markdown.application.port.MarkdownNormalizationPort;
import studio.one.platform.markdown.domain.MarkdownResource;
import studio.one.platform.textract.domain.model.BlockType;
import studio.one.platform.textract.domain.model.ExtractedImage;
import studio.one.platform.textract.domain.model.ExtractedTable;
import studio.one.platform.textract.domain.model.ParsedBlock;
import studio.one.platform.textract.domain.model.ParsedFile;
import studio.one.platform.textract.domain.model.DocumentFormat;

class MarkdownNormalizationComponentsTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsParsedFileBlocksTablesImagesAndFallbackMetadata() {
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                "Policy text",
                List.of(ParsedBlock.text("b1", BlockType.HEADING, "Policy", 2, 3,
                        Map.of("sourceRef", "page-2:block-1"))),
                Map.of("author", "tester"),
                List.of(),
                List.of(),
                List.of(new ExtractedTable("t1", "| A |\n| --- |\n| B |", List.of(),
                        Map.of("sourceRef", "table-1"))),
                List.of(new ExtractedImage("img1", "image/png", "chart.png", 100, 80,
                        Map.of("sourceRef", "image-1", "caption", "Chart caption", "page", 2))),
                false,
                "",
                "text",
                List.of());

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-1", "policy.pdf", "pdf", "");

        assertThat(document.blocks()).extracting(NormalizedBlock::type)
                .contains(NormalizedBlockType.HEADING, NormalizedBlockType.TABLE,
                        NormalizedBlockType.IMAGE, NormalizedBlockType.IMAGE_CAPTION);
        NormalizedBlock heading = document.blocks().stream()
                .filter(block -> block.type() == NormalizedBlockType.HEADING)
                .findFirst()
                .orElseThrow();
        assertThat(heading.page()).isEqualTo(2);
        assertThat(heading.sourceRef()).isEqualTo("page-2:block-1");
        assertThat(document.metadata()).containsEntry("author", "tester");
    }

    @Test
    void reclassifiesOcrBlocksBeforeRenderingMarkdown() {
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                "개념\n001 다음식을 전개하시오.\n고대.그리스의수학자유클리드",
                List.of(
                        ParsedBlock.text("b1", BlockType.OCR_TEXT, "개념", 1, 0,
                                Map.of("ocrApplied", true, "sourceRef", "page[1]/block[0]")),
                        ParsedBlock.text("b2", BlockType.OCR_TEXT, "001 다음식을 전개하시오.", 1, 1,
                                Map.of("ocrApplied", true, "sourceRef", "page[1]/block[1]")),
                        ParsedBlock.text("b3", BlockType.OCR_TEXT, "i ss NOS", 1, 2,
                                Map.of("ocrApplied", true, "sourceRef", "page[1]/block[2]")),
                        ParsedBlock.text("b4", BlockType.OCR_TEXT, "고대.그리스의수학자유클리드", 1, 3,
                                Map.of("ocrApplied", true, "sourceRef", "page[1]/block[3]"))),
                Map.of("ocrApplied", true),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true);

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-ocr", "math.pdf", "pdf", "");
        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(document.blocks()).extracting(NormalizedBlock::type)
                .contains(NormalizedBlockType.HEADING, NormalizedBlockType.LIST_ITEM, NormalizedBlockType.PARAGRAPH)
                .doesNotContain(NormalizedBlockType.OCR_TEXT);
        assertThat(markdown).contains("## 개념", "- 001 다음식을 전개하시오.");
        assertThat(markdown).doesNotContain("i ss NOS");
        NormalizedBlock pageContext = document.blocks().stream()
                .filter(block -> Boolean.TRUE.equals(block.metadata().get("searchContextOnly")))
                .filter(block -> block.type() == NormalizedBlockType.PAGE)
                .findFirst()
                .orElseThrow();
        assertThat(pageContext.type()).isEqualTo(NormalizedBlockType.PAGE);
        assertThat(pageContext.page()).isEqualTo(1);
        assertThat(pageContext.text()).contains("Page 1", "개념", "001 다음식을 전개하시오.");
        assertThat(markdown).doesNotContain("Page 1");
    }

    @Test
    void normalizesMathOcrNoiseAndMergesShortKoreanFragments() {
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                "다항식의정\n리\nOO\nxㅡ6x+3",
                List.of(
                        ParsedBlock.text("b1", BlockType.OCR_TEXT, "다항식의정\nOO\nSis!", 1, 0,
                                Map.of("ocrApplied", true, "sourceRef", "page[1]/block[0]",
                                        "bbox", List.of(10, 20, 100, 30))),
                        ParsedBlock.text("b2", BlockType.OCR_TEXT, "리", 1, 1,
                                Map.of("ocrApplied", true, "sourceRef", "page[1]/block[1]",
                                        "bbox", List.of(101, 21, 110, 31))),
                        ParsedBlock.text("b3", BlockType.OCR_TEXT, "OO", 1, 2,
                                Map.of("ocrApplied", true, "sourceRef", "page[1]/block[2]")),
                        ParsedBlock.text("b4", BlockType.OCR_TEXT, "xㅡ6x+3", 1, 3,
                                Map.of("ocrApplied", true, "sourceRef", "page[1]/block[3]")),
                        ParsedBlock.text("b5", BlockType.OCR_TEXT, "2aN>", 1, 4,
                                Map.of("ocrApplied", true, "sourceRef", "page[1]/block[4]")),
                        ParsedBlock.text("b6", BlockType.OCR_TEXT, "eT 00", 1, 5,
                                Map.of("ocrApplied", true, "sourceRef", "page[1]/block[5]")),
                        ParsedBlock.text("b7", BlockType.OCR_TEXT, "다항", 1, 6,
                                Map.of("ocrApplied", true, "sourceRef", "page[1]/block[6]",
                                        "bbox", List.of(10, 60, 30, 70))),
                        ParsedBlock.text("b8", BlockType.OCR_TEXT, "식의정리", 1, 7,
                                Map.of("ocrApplied", true, "sourceRef", "page[1]/block[7]",
                                        "bbox", List.of(31, 61, 90, 71)))),
                Map.of("ocrApplied", true),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true);

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-ocr", "math.pdf", "pdf", "");
        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown).contains("다항식의정리", "x-6x+3");
        assertThat(markdown).doesNotContain("OO");
        assertThat(markdown).doesNotContain("2aN>", "eT 00");
        assertThat(document.blocks()).anyMatch(block -> Boolean.TRUE.equals(block.metadata().get("lineMergeApplied")));
        assertThat(document.blocks()).anyMatch(block -> Boolean.TRUE.equals(block.metadata().get("searchContextOnly"))
                && block.text().contains("다항식의정리")
                && block.text().contains("x-6x+3"));
        assertThat(document.metadata()).containsEntry("discardedOcrNoiseCount", 3L);
        assertThat(document.metadata()).containsKey("pageQuality");
    }

    @Test
    void composesPageParagraphsAndClustersShortMathFragments() throws Exception {
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                "다항\n식의정리\n해보자.\n=\n2\n$x^2$\n$-6x$",
                List.of(
                        ParsedBlock.text("b1", BlockType.OCR_TEXT, "다항", 4, 0,
                                Map.of("ocrApplied", true, "sourceRef", "page[4]/block[0]")),
                        ParsedBlock.text("b2", BlockType.OCR_TEXT, "식의정리", 4, 1,
                                Map.of("ocrApplied", true, "sourceRef", "page[4]/block[1]")),
                        ParsedBlock.text("b3", BlockType.OCR_TEXT, "해보자.", 4, 2,
                                Map.of("ocrApplied", true, "sourceRef", "page[4]/block[2]")),
                        ParsedBlock.text("b4", BlockType.OCR_TEXT, "=", 4, 3,
                                Map.of("ocrApplied", true, "sourceRef", "page[4]/block[3]")),
                        ParsedBlock.text("b5", BlockType.OCR_TEXT, "2", 4, 4,
                                Map.of("ocrApplied", true, "sourceRef", "page[4]/block[4]")),
                        ParsedBlock.text("b6", BlockType.OCR_TEXT, "$x^2$", 4, 5,
                                Map.of("ocrApplied", true, "sourceRef", "page[4]/block[5]")),
                        ParsedBlock.text("b7", BlockType.OCR_TEXT, "$-6x$", 4, 6,
                                Map.of("ocrApplied", true, "sourceRef", "page[4]/block[6]"))),
                Map.of("ocrApplied", true),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true);

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-page-compose", "math.pdf", "pdf", "");
        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown).contains("다항식의정리해보자.");
        assertThat(markdown).contains("$x^2$ $-6x$");
        assertThat(markdown).doesNotContain("\n\n=\n\n");
        assertThat(markdown).doesNotContain("\n\n2\n\n");
        assertThat(document.metadata()).containsKeys("markdownShortLineRatio", "markdownQualityTargetShortLineRatio",
                "markdownQualityTargetScore", "markdownMathBlockCount", "normalizedShortLineRatio");
        assertThat((Double) document.metadata().get("markdownShortLineRatio")).isLessThan(0.20d);
        assertThat(document.blocks()).anyMatch(block -> "PAGE_PARAGRAPH_COMPOSER"
                .equals(block.metadata().get("lineMergeReason")));
        assertThat(document.blocks()).anyMatch(block -> "MATH_CLUSTER_FRAGMENT"
                .equals(block.metadata().get("lineMergeReason")));

        MarkdownResource resource = NormalizedDocumentSnapshot.resource("mrev-page-compose", document,
                NormalizedDocumentSnapshot.SOURCE_NATIVE, List.of(), objectMapper);
        Map<String, Object> payload = objectMapper.readValue(resource.metadataJson(), new TypeReference<>() {
        });
        assertThat(payload).containsKeys("markdownShortLineRatio", "markdownQualityTargetShortLineRatio",
                "markdownQualityTargetScore", "markdownMathBlockCount", "normalizedShortLineRatio");
    }

    @Test
    void postProcessesRenderedMarkdownAndRecordsRenderedQualityMetrics() throws Exception {
        NormalizedDocument document = NormalizedDocument.builder("doc-rendered")
                .sourceFormat("pdf")
                .filename("math.pdf")
                .metadata(Map.of("ocrApplied", true, "pdfExtractionEngine", "pymupdf4llm"))
                .blocks(List.of(
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                        "다항\n식의정리\n=\n2\n$x^2$\n$-6x$\n/ oy~")
                                .page(1)
                                .sourceRef("page[1]/block[0]")
                                .metadata(Map.of("ocrApplied", true, "originalType", "OCR_TEXT"))
                                .order(0)
                                .build()))
                .build();

        NormalizedMarkdownRenderer renderer = new NormalizedMarkdownRenderer();
        String markdown = renderer.render(document, "");
        List<String> issues = new NormalizedDocumentQualityValidator().validate(document, markdown);
        NormalizedDocument withMetrics = new RenderedMarkdownPostProcessor()
                .withRenderedQuality(document, markdown, issues);
        MarkdownResource resource = NormalizedDocumentSnapshot.resource("mrev-rendered", withMetrics,
                NormalizedDocumentSnapshot.SOURCE_NATIVE, issues, objectMapper);
        Map<String, Object> payload = objectMapper.readValue(resource.metadataJson(), new TypeReference<>() {
        });

        assertThat(markdown).contains("다항식의정리");
        assertThat(markdown).contains("2 $x^2$ $-6x$");
        assertThat(markdown).doesNotContain("\n=\n");
        assertThat(markdown).doesNotContain("/ oy~");
        assertThat(payload).containsKeys("renderedMarkdownShortLineRatio", "renderedMarkdownShortLineCount",
                "renderedMarkdownFormulaLineCount", "renderedMarkdownNoiseLineCount",
                "renderedMarkdownMinusSuspectCount");
        assertThat((Double) payload.get("markdownShortLineRatio"))
                .isEqualTo((Double) payload.get("renderedMarkdownShortLineRatio"));
        assertThat((Double) payload.get("renderedMarkdownShortLineRatio")).isLessThan(0.20d);
    }

    @Test
    void postProcessesProblemNumbersTableFragmentsSymbolsAndStandaloneDigits() {
        String markdown = """
                001
                다음식을 전개하시오.

                |

                | 문제2
                두다항식의 합을 구하시오.

                | 참고
                교환법칙:
                덧셈에서는 순서를 바꾸어도 같다.

                °

                ㅡ

                41800

                2
                $x^2$
                $-6x$

                | A |
                | --- |
                | B |

                | | C |

                C $8 04~06

                2 @

                @

                ㅜㅠ

                (/)

                다항식 7" ㅡ682 3

                ㅡ--1

                ㅡㅠㅜ2)를전개하면

                xe ㅡ-42=

                arty’

                THE]

                」

                ·

                ㅇ

                ©

                A+B

                =2r+3

                004@

                040@

                2ㅋ-3

                1)2=9 일때, (2ㅡ +r 을구히 |

                개념

                참고

                하면

                다.

                see ©

                wee ®

                %

                ]

                »

                、,

                ……
                """;

        String processed = new RenderedMarkdownPostProcessor().postProcess(markdown);
        Map<String, Object> metrics = new RenderedMarkdownPostProcessor().metrics(processed);

        assertThat(processed).contains("001 다음식을 전개하시오.");
        assertThat(processed).contains("문제2 두다항식의 합을 구하시오.");
        assertThat(processed).contains("참고 교환법칙: 덧셈에서는 순서를 바꾸어도 같다.");
        assertThat(processed).contains("2 $x^2$ $-6x$");
        assertThat(processed).contains("| A |\n| --- |\n| B |");
        assertThat(processed).contains("| C |");
        assertThat(processed).contains("다항식 7\"-682 3");
        assertThat(processed).contains("-1");
        assertThat(processed).contains("-2)를전개하면");
        assertThat(processed).contains("xe -42=");
        assertThat(processed).contains("A+B");
        assertThat(processed).contains("=2r+3");
        assertThat(processed).contains("004");
        assertThat(processed).contains("040");
        assertThat(processed).contains("2-3");
        assertThat(processed).contains("2-+r");
        assertThat(processed).doesNotContain("\n|\n");
        assertThat(processed).doesNotContain("°");
        assertThat(processed).doesNotContain("ㅡ");
        assertThat(processed).doesNotContain("41800");
        assertThat(processed).doesNotContain("\n@\n");
        assertThat(processed).doesNotContain("ㅜㅠ");
        assertThat(processed).doesNotContain("(/)");
        assertThat(processed).doesNotContain("arty");
        assertThat(processed).doesNotContain("THE]");
        assertThat(processed).doesNotContain("」");
        assertThat(processed).doesNotContain("·");
        assertThat(processed).doesNotContain("\nㅇ\n");
        assertThat(processed).doesNotContain("©");
        assertThat(processed).doesNotContain("ㅋ");
        assertThat(processed).doesNotContain("see");
        assertThat(processed).doesNotContain("wee");
        assertThat(processed).doesNotContain("%");
        assertThat(processed).doesNotContain("]");
        assertThat(processed).doesNotContain("»");
        assertThat(processed).doesNotContain("、");
        assertThat(processed).doesNotContain("……");
        assertThat(processed).doesNotContain("$8");
        assertThat((Boolean) metrics.get("renderedMarkdownBrokenLatexDelimiter")).isFalse();
        assertThat((Double) metrics.get("renderedMarkdownShortLineRatio")).isLessThan(0.20d);
    }

    @Test
    void suppressesOverlappingBrokenMathWhenVisionCorrectionExists() {
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                "$x^2+1$\"",
                List.of(
                        ParsedBlock.text("ocr-broken", BlockType.OCR_TEXT, "$x^2+1$\"", 2, 0,
                                Map.of("ocrApplied", true, "sourceRef", "page[2]/block[0]",
                                        "bbox", List.of(10, 20, 100, 40))),
                        ParsedBlock.text("vision-formula", BlockType.PARAGRAPH, "$x^{2}+1=0$", 2, 1,
                                Map.of("sourceRef", "math-vision/page[2]/formula[0]",
                                        "bbox", List.of(12, 21, 98, 39),
                                        "mathVisionCorrectionOnly", true,
                                        "mathSupplementSource", "VISION_LLM"))),
                Map.of("ocrApplied", true, "mathVisionCorrectionApplied", true),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true);

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-vision", "math.pdf", "pdf", "");
        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown).contains("$x^{2}+1=0$");
        assertThat(markdown).doesNotContain("$x^2+1$\"");
        assertThat(document.blocks()).anyMatch(block -> Boolean.TRUE.equals(block.metadata().get("suppressedByMathCorrection")));
        assertThat(document.metadata()).containsEntry("mathReplacementCount", 1L);
    }

    @Test
    void suppressesBrokenMathByPageTokenSimilarityWhenBboxIsMissing() {
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                "$x^2+1$\"",
                List.of(
                        ParsedBlock.text("ocr-broken", BlockType.OCR_TEXT, "xㅡ2+1=$", 3, 0,
                                Map.of("ocrApplied", true, "sourceRef", "page[3]/block[0]")),
                        ParsedBlock.text("vision-formula", BlockType.PARAGRAPH, "$x^{2}+1=0$", 3, 1,
                                Map.of("sourceRef", "math-vision/page[3]/formula[0]",
                                        "mathVisionCorrectionOnly", true,
                                        "mathSupplementSource", "VISION_LLM"))),
                Map.of("ocrApplied", true, "mathVisionCorrectionApplied", true),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true);

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-vision", "math.pdf", "pdf", "");
        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown).contains("$x^{2}+1=0$");
        assertThat(markdown).doesNotContain("x-2+1=$");
        assertThat(document.metadata()).containsEntry("mathReplacementStrategy", "BBOX_OR_PAGE_WINDOW");
    }

    @Test
    void placesHybridMathCorrectionsAfterTheirPageContent() {
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                "첫째 본문\n둘째 본문\n$x^2+1$",
                List.of(
                        ParsedBlock.text("page-one", BlockType.PARAGRAPH, "첫째 본문입니다.", 1, 0,
                                Map.of("sourceRef", "page[1]/block[0]")),
                        ParsedBlock.text("page-two", BlockType.PARAGRAPH, "둘째 본문입니다.", 2, 0,
                                Map.of("sourceRef", "page[2]/block[0]")),
                        ParsedBlock.text("page-two-formula", BlockType.PARAGRAPH, "$x^2+1$", 2, 0,
                                Map.of("sourceRef", "math-supplement/page[2]/formula[0]",
                                        "mathSupplementOnly", true,
                                        "mathSupplementSource", "HYBRID_MATH_OCR"))),
                Map.of("ocrApplied", true),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true);

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-hybrid-order", "math.pdf", "pdf", "");
        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown.indexOf("첫째 본문입니다."))
                .isLessThan(markdown.indexOf("둘째 본문입니다."));
        assertThat(markdown.indexOf("둘째 본문입니다."))
                .isLessThan(markdown.indexOf("$x^2+1$"));
        assertThat(document.blocks().stream()
                .filter(block -> !Boolean.TRUE.equals(block.metadata().get("searchContextOnly")))
                .toList()).extracting(NormalizedBlock::order)
                .containsExactly(0, 1, 2);
    }

    @Test
    void replacesBaselinePageContentWhenPix2TextProvidesStructuredPageBlocks() {
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                "baseline\nreplacement",
                List.of(
                        ParsedBlock.text("native", BlockType.OCR_TEXT, "손상된 HUH 본문", 3, 0,
                                Map.of("ocrApplied", true, "sourceRef", "page[3]/ocr[0]")),
                        ParsedBlock.text("pix-heading", BlockType.HEADING, "다항식의 연산", 3, 1,
                                Map.of("sourceRef", "math-page/page[3]/block[0]",
                                        "mathPageContentReplacement", true,
                                        "mathSupplementSource", "HYBRID_MATH_OCR")),
                        ParsedBlock.text("pix-body", BlockType.PARAGRAPH, "다항식의 덧셈과 곱셈을 정리한다.", 3, 2,
                                Map.of("sourceRef", "math-page/page[3]/block[1]",
                                        "mathPageContentReplacement", true,
                                        "mathSupplementSource", "HYBRID_MATH_OCR"))),
                Map.of("ocrApplied", true),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true);

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-page-replacement", "math.pdf", "pdf", "");
        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown).contains("다항식의 연산", "다항식의 덧셈과 곱셈을 정리한다.");
        assertThat(markdown).doesNotContain("손상된 HUH 본문");
        assertThat(document.blocks()).anyMatch(block -> "REPLACED_BY_MATH_PAGE_OCR"
                .equals(block.metadata().get("discardReason")));
    }

    @Test
    void structuredMathPageReplacementWinsWhenKoreanCorrectionTargetsSamePage() {
        String garbled = "CrefAlo ChStAJo Cerio HUH GES latin replacement text ".repeat(8);
        String korean = "다항식의 연산과 곱셈 공식을 설명하는 본문입니다. ".repeat(3);
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                garbled,
                List.of(
                        ParsedBlock.text("native", BlockType.PARAGRAPH, garbled, 3, 0,
                                Map.of("sourceRef", "page[3]/block[0]")),
                        ParsedBlock.text("korean", BlockType.PARAGRAPH, korean, 3, 1,
                                Map.of("sourceRef", "korean-ocr/page[3]/block[0]",
                                        "textCorrectionOnly", true,
                                        "textPageContentReplacement", true,
                                        "catastrophicKoreanBaseline", true)),
                        ParsedBlock.text("pix-heading", BlockType.HEADING, "다항식의 연산", 3, 2,
                                Map.of("sourceRef", "math-page/page[3]/block[0]",
                                        "mathPageContentReplacement", true,
                                        "mathSupplementSource", "HYBRID_MATH_OCR")),
                        ParsedBlock.text("pix-body", BlockType.PARAGRAPH, "$(a+b)^2=a^2+2ab+b^2$", 3, 3,
                                Map.of("sourceRef", "math-page/page[3]/block[1]",
                                        "mathPageContentReplacement", true,
                                        "mathSupplementSource", "HYBRID_MATH_OCR"))),
                Map.of("ocrApplied", true, "koreanTextOcrApplied", true),
                List.of(), List.of(), List.of(), List.of(), true);

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-math-page-priority", "math.pdf", "pdf", "");
        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown)
                .contains("다항식의 연산", "$(a+b)^2=a^2+2ab+b^2$")
                .doesNotContain("CrefAlo", korean.trim());
    }

    @Test
    void replacesBrokenKoreanProseOnlyWhenCorrectionHasBetterCoverageAndFewerJamo() {
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                "손상된 본문",
                List.of(
                        ParsedBlock.text("native", BlockType.OCR_TEXT,
                                "다항식의 ㅠ산은 여러 항을 더하고 곱하는 ㅣ정을 설명하며 계산 원리를 단계별로 정리한다.", 4, 0,
                                Map.of("ocrApplied", true, "sourceRef", "page[4]/ocr[0]")),
                        ParsedBlock.text("paddle", BlockType.PARAGRAPH,
                                "다항식의 연산은 여러 항을 더하고 곱하는 과정을 설명하며 계산 원리를 단계별로 정리한다.", 4, 1,
                                Map.of("sourceRef", "korean-ocr/page[4]/block[0]",
                                        "textCorrectionOnly", true,
                                        "textPageContentReplacement", true,
                                        "textCorrectionProvider", "paddleocr",
                                        "mergePriority", 300))),
                Map.of("ocrApplied", true, "koreanTextOcrApplied", true),
                List.of(), List.of(), List.of(), List.of(), true);

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-korean", "math.pdf", "pdf", "");
        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown).contains("다항식의 연산은 여러 항을 더하고 곱하는 과정을 설명하며 계산 원리를 단계별로 정리한다.")
                .doesNotContain("ㅠ산", "ㅣ정을");
        assertThat(document.blocks()).anyMatch(block -> "REPLACED_BY_KOREAN_TEXT_OCR"
                .equals(block.metadata().get("discardReason")));
    }

    @Test
    void replacesLongLatinGarblingWithShorterKoreanCorrectionForCatastrophicBaseline() {
        String garbled = "CrefAlo ChStAJo Cerio HUH GES latin replacement text ".repeat(10);
        String corrected = "다항식의 연산은 여러 항을 더하고 곱하는 과정을 설명한다. ".repeat(3);
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                garbled,
                List.of(
                        ParsedBlock.text("native", BlockType.PARAGRAPH, garbled, 2, 0,
                                Map.of("sourceRef", "page[2]/block[0]")),
                        ParsedBlock.text("paddle", BlockType.PARAGRAPH, corrected, 2, 1,
                                Map.of("sourceRef", "korean-ocr/page[2]/block[0]",
                                        "textCorrectionOnly", true,
                                        "textPageContentReplacement", true,
                                        "catastrophicKoreanBaseline", true,
                                        "textCorrectionProvider", "paddleocr",
                                        "mergePriority", 300))),
                Map.of("ocrApplied", true, "koreanTextOcrApplied", true),
                List.of(), List.of(), List.of(), List.of(), true);

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-korean-latin", "미래엔_고등수학.pdf", "pdf", "");
        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown).contains("다항식의 연산은 여러 항을 더하고 곱하는 과정을 설명한다.")
                .doesNotContain("CrefAlo", "ChStAJo");
        assertThat(document.blocks()).anyMatch(block -> "REPLACED_BY_KOREAN_TEXT_OCR"
                .equals(block.metadata().get("discardReason")));
    }

    @Test
    void koreanPageReplacementDropsBaselineBodyAndTableButKeepsValidatedFormula() {
        String garbled = "CrefAlo ChStAJo Cerio HUH GES latin replacement text ".repeat(10);
        String corrected = "다항식의 연산은 여러 항을 더하고 곱하는 과정을 설명한다. ".repeat(3);
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                garbled,
                List.of(
                        ParsedBlock.text("baseline-body", BlockType.PARAGRAPH, garbled, 2, 0,
                                Map.of("sourceRef", "page[2]/block[0]")),
                        ParsedBlock.text("baseline-table", BlockType.TABLE,
                                "| BROKEN_TABLE | CrefAlo |", 2, 1,
                                Map.of("sourceRef", "page[2]/table[0]")),
                        ParsedBlock.text("baseline-formula", BlockType.PARAGRAPH,
                                "$x^{2}+2x+1=0$", 2, 2,
                                Map.of("sourceRef", "page[2]/formula[0]")),
                        ParsedBlock.text("invalid-formula", BlockType.PARAGRAPH,
                                "CrefAlo x+1=2 CHStAJo", 2, 3,
                                Map.of("sourceRef", "math-vision/page[2]/formula[0]",
                                        "mathVisionCorrectionOnly", true,
                                        "mathSupplementSource", "VISION_LLM")),
                        ParsedBlock.text("korean-correction", BlockType.PARAGRAPH, corrected, 2, 4,
                                Map.of("sourceRef", "korean-ocr/page[2]/block[0]",
                                        "textCorrectionOnly", true,
                                        "textPageContentReplacement", true,
                                        "catastrophicKoreanBaseline", true,
                                        "textCorrectionProvider", "pymupdf4llm-korean-ocr"))),
                Map.of("ocrApplied", true, "koreanTextOcrApplied", true),
                List.of(), List.of(), List.of(), List.of(), true);

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-filtered-korean", "math.pdf", "pdf", "");
        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown)
                .contains(corrected.trim(), "$x^{2}+2x+1=0$")
                .doesNotContain("CrefAlo", "CHStAJo", "BROKEN_TABLE");
        assertThat(document.blocks().stream()
                .filter(block -> "REPLACED_BY_KOREAN_TEXT_OCR".equals(block.metadata().get("discardReason"))))
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(document.blocks()).anyMatch(block -> "invalid-formula".equals(block.id())
                && Boolean.TRUE.equals(block.metadata().get("searchContextOnly")));
    }

    @Test
    void koreanPageReplacementKeepsProblemIdentifiersWithoutRestoringDiscardedBody() {
        String garbled = "0256 CrefAlo broken body 026중 more noise 0276";
        String corrected = "다항식의 전개식에서 각 항의 계수를 구하는 과정을 설명한다. ".repeat(3);
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                garbled,
                List.of(
                        ParsedBlock.text("baseline", BlockType.PARAGRAPH, garbled, 34, 0,
                                Map.of("sourceRef", "page[34]/block[0]")),
                        ParsedBlock.text("correction", BlockType.PARAGRAPH, corrected, 34, 1,
                                Map.of("sourceRef", "korean-ocr/page[34]/block[0]",
                                        "textCorrectionOnly", true,
                                        "textPageContentReplacement", true,
                                        "catastrophicKoreanBaseline", true,
                                        "textCorrectionProvider", "paddleocr"))),
                Map.of("ocrApplied", true, "koreanTextOcrApplied", true),
                List.of(), List.of(), List.of(), List.of(), true);

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-problem-markers", "math.pdf", "pdf", "");
        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown)
                .contains("### 문제 025", "### 문제 026", "### 문제 027", corrected.trim())
                .doesNotContain("CrefAlo", "broken body", "more noise");
        assertThat(document.blocks().stream()
                .filter(block -> Boolean.TRUE.equals(block.metadata().get("derivedProblemMarker"))))
                .extracting(NormalizedBlock::page, block -> block.metadata().get("problemNumber"))
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(34, "025"),
                        org.assertj.core.groups.Tuple.tuple(34, "026"),
                        org.assertj.core.groups.Tuple.tuple(34, "027"));
    }

    @Test
    void replacesMathHeavyCatastrophicPageWhenKoreanCorrectionContainsEquations() {
        String garbled = "CHStA!O| LEA f(x)=(2x-1)(2x+3)+5 CrefAlo GES ".repeat(14);
        String corrected = "다항식의 값을 계산하면 f(x)=(2x-1)(2x+3)+5이고, 전개한 뒤 동류항을 정리한다. ".repeat(3);
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                garbled,
                List.of(
                        ParsedBlock.text("baseline-math-heavy", BlockType.PARAGRAPH, garbled, 39, 0,
                                Map.of("sourceRef", "page[39]/block[0]")),
                        ParsedBlock.text("korean-math-heavy", BlockType.PARAGRAPH, corrected, 39, 1,
                                Map.of("sourceRef", "korean-ocr/page[39]/block[0]",
                                        "textCorrectionOnly", true,
                                        "textPageContentReplacement", true,
                                        "catastrophicKoreanBaseline", true,
                                        "textCorrectionProvider", "paddleocr"))),
                Map.of("ocrApplied", true, "koreanTextOcrApplied", true),
                List.of(), List.of(), List.of(), List.of(), true);

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-math-heavy", "math.pdf", "pdf", "");
        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown).contains("다항식의 값을 계산하면").doesNotContain("CHStA", "CrefAlo");
        assertThat(document.blocks()).anyMatch(block -> "baseline-math-heavy".equals(block.id())
                && "REPLACED_BY_KOREAN_TEXT_OCR".equals(block.metadata().get("discardReason")));
    }

    @Test
    void replacesCatastrophicPageEvenWhenGarbledBaselineContainsSubstantialHangul() {
        String garbled = ("다항식 Lecture CHStA CrefAlo 계산 GES x+1=2 깨진본문 ").repeat(35);
        String corrected = ("다항식의 나눗셈은 몫과 나머지를 이용하여 계산한다. ").repeat(6);
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                garbled,
                List.of(
                        ParsedBlock.text("mixed-garbled", BlockType.PARAGRAPH, garbled, 39, 0,
                                Map.of("sourceRef", "page[39]/block[0]")),
                        ParsedBlock.text("clean-korean", BlockType.OCR_TEXT, corrected, 39, 1,
                                Map.of("sourceRef", "korean-ocr/page[39]/block[0]",
                                        "textCorrectionOnly", true,
                                        "textPageContentReplacement", true,
                                        "catastrophicKoreanBaseline", true,
                                        "textCorrectionProvider", "paddleocr"))),
                Map.of("ocrApplied", false, "koreanTextOcrApplied", true),
                List.of(), List.of(), List.of(), List.of(), true);

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-mixed-garbled", "math.pdf", "pdf", "");
        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown).contains("다항식의 나눗셈은 몫과 나머지를 이용하여 계산한다.")
                .doesNotContain("Lecture", "CHStA", "CrefAlo", "GES");
        assertThat(document.blocks()).anyMatch(block -> "mixed-garbled".equals(block.id())
                && "REPLACED_BY_KOREAN_TEXT_OCR".equals(block.metadata().get("discardReason")));
    }

    @Test
    void keepsNormalPageContentWhenNoKoreanReplacementIsEligible() {
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                "정상 본문 $x+1=2$",
                List.of(
                        ParsedBlock.text("body", BlockType.PARAGRAPH, "정상적인 다항식 설명 본문입니다.", 1, 0,
                                Map.of("sourceRef", "page[1]/block[0]")),
                        ParsedBlock.text("table", BlockType.TABLE, "| 항 | 계수 |", 1, 1,
                                Map.of("sourceRef", "page[1]/table[0]")),
                        ParsedBlock.text("formula", BlockType.PARAGRAPH, "$x+1=2$", 1, 2,
                                Map.of("sourceRef", "page[1]/formula[0]"))),
                Map.of(), List.of(), List.of(), List.of(), List.of(), false);

        String markdown = new NormalizedMarkdownRenderer().render(
                new ParsedFileNormalizedDocumentMapper().map(parsed, "mrev-normal", "normal.pdf", "pdf", ""), "");

        assertThat(markdown).contains("정상적인 다항식 설명 본문입니다.", "항", "계수", "$x+1=2$");
    }

    @Test
    void rendererExcludesDiscardedAndSearchContextBlocksIndependently() {
        NormalizedDocument document = NormalizedDocument.builder("mrev-render-filter")
                .blocks(List.of(
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "discarded text")
                                .id("discarded")
                                .metadata(Map.of("discardedOcrNoise", true))
                                .build(),
                        NormalizedBlock.builder(NormalizedBlockType.PAGE, "search context")
                                .id("search")
                                .metadata(Map.of("searchContextOnly", true))
                                .build(),
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "정상적으로 표시할 본문입니다.")
                                .id("visible")
                                .build()))
                .build();

        assertThat(new NormalizedMarkdownRenderer().render(document, "unsafe fallback"))
                .isEqualTo("정상적으로 표시할 본문입니다.");
    }

    @Test
    void rendererDoesNotRestoreFallbackWhenEveryBlockIsExcluded() {
        NormalizedDocument document = NormalizedDocument.builder("mrev-all-hidden")
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "CrefAlo broken text")
                        .id("hidden")
                        .metadata(Map.of("discardedOcrNoise", true))
                        .build()))
                .build();

        assertThat(new NormalizedMarkdownRenderer().render(document, "CrefAlo unsafe fallback"))
                .isEmpty();
    }

    @Test
    void koreanTextOcrSatisfiesRecommendedOcrDiagnostic() {
        NormalizedDocument document = NormalizedDocument.builder("mrev-effective-ocr")
                .filename("한글수학교재.pdf")
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                        "다항식의 연산과 곱셈 공식을 설명한다.")
                        .id("body")
                        .page(1)
                        .sourceRef("page[1]/block[0]")
                        .build()))
                .metadata(Map.of(
                        "ocrApplied", false,
                        "koreanTextOcrApplied", true,
                        "pdfAnalysis", Map.of("ocrRecommended", true, "pageCount", 1)))
                .build();

        assertThat(new NormalizedDocumentQualityValidator().validate(document,
                "다항식의 연산과 곱셈 공식을 설명한다."))
                .doesNotContain("OCR_RECOMMENDED_BUT_NOT_APPLIED");
    }

    @Test
    void deduplicatesMathCorrectionBlocksAndDropsTinyFormulaNoise() {
        ParsedFile parsed = new ParsedFile(
                DocumentFormat.PDF,
                "$x^{2}+1=0$",
                List.of(
                        ParsedBlock.text("vision-formula-1", BlockType.PARAGRAPH, "$x^{2}+1=0$", 7, 0,
                                Map.of("sourceRef", "math-vision/page[7]/formula[0]",
                                        "mathVisionCorrectionOnly", true,
                                        "mathSupplementSource", "VISION_LLM")),
                        ParsedBlock.text("vision-formula-2", BlockType.PARAGRAPH, "$x^2+1=0$", 7, 1,
                                Map.of("sourceRef", "math-vision/page[7]/formula[1]",
                                        "mathVisionCorrectionOnly", true,
                                        "mathSupplementSource", "VISION_LLM")),
                        ParsedBlock.text("vision-noise", BlockType.PARAGRAPH, "$1$", 7, 2,
                                Map.of("sourceRef", "math-vision/page[7]/formula[2]",
                                        "mathVisionCorrectionOnly", true,
                                        "mathSupplementSource", "VISION_LLM"))),
                Map.of("mathVisionCorrectionApplied", true),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true);

        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper()
                .map(parsed, "mrev-vision-dedupe", "math.pdf", "pdf", "");
        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown).contains("$x^{2}+1=0$");
        assertThat(markdown).doesNotContain("$1$");
        assertThat(document.metadata()).containsEntry("deduplicatedMathCorrectionCount", 1L)
                .containsEntry("discardedMathCorrectionNoiseCount", 1L);
    }

    @Test
    void postProcessorRemovesMixedLatinHangulOcrNoiseAndTinyFormulaLines() {
        String markdown = """
                Lecture 다항식의곱셈

                F805/ SSHEO| 있는다항식의전개디디유

                다항식의Lhe디디

                론,을집대성한SAS 가장높게평가받고있는데

                $x^{2}+1=0$

                $x^2+1=0$

                $1$
                """;

        String processed = new RenderedMarkdownPostProcessor().postProcess(markdown);

        assertThat(processed).contains("다항식의곱셈");
        assertThat(processed).contains("있는다항식의전개디디유");
        assertThat(processed).contains("다항식의");
        assertThat(processed).contains("론,을집대성한 가장높게평가받고있는데");
        assertThat(processed).contains("$x^{2}+1=0$");
        assertThat(processed).doesNotContain("Lecture", "SSHEO", "Lhe디디", "SAS", "$1$");
    }

    @Test
    void qualityMetricsAllowKoreanChoiceMarkersAndRejectJamoInsideFormula() {
        String markdown = """
                ㄱ의 전개식에서 x항을 구한다.
                ㄴ을 식에 대입한다.
                ㄷ을 더한다.
                따라서 a=b이ㅁ로 답은 4이다.
                $x^2+ㅜ82$
                """;

        String normalized = OcrTextNormalizer.normalize(markdown);
        String processed = new RenderedMarkdownPostProcessor().postProcess(normalized);
        Map<String, Object> metrics = new RenderedMarkdownPostProcessor().metrics(processed);

        assertThat(processed).contains("ㄱ의", "ㄴ을", "ㄷ을", "a=b이므로")
                .doesNotContain("ㅜ82");
        assertThat(metrics).containsEntry("renderedMarkdownJamoLineCount", 0L);
    }

    @Test
    void rendersNormalizedDocumentToMarkdown() {
        NormalizedDocument document = NormalizedDocument.builder("doc-1")
                .blocks(List.of(
                        NormalizedBlock.builder(NormalizedBlockType.TITLE, "Title").order(0).build(),
                        NormalizedBlock.builder(NormalizedBlockType.LIST_ITEM, "First").order(1).build(),
                        NormalizedBlock.builder(NormalizedBlockType.TABLE, "fallback")
                                .order(2)
                                .metadata(Map.of("markdown", "| A |\n| --- |\n| B |"))
                                .build(),
                        NormalizedBlock.builder(NormalizedBlockType.IMAGE, "Chart")
                                .order(3)
                                .sourceRef("image-1")
                                .metadata(Map.of("altText", "Chart"))
                                .build()))
                .build();

        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown).contains("# Title");
        assertThat(markdown).contains("- First");
        assertThat(markdown).contains("| A |");
        assertThat(markdown).contains("![Chart](image-1)");
    }

    @Test
    void compactsProseBlankLinesAndRendersQuestionStructure() {
        NormalizedDocument document = NormalizedDocument.builder("doc-1")
                .blocks(List.of(
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "문제 12").order(0).build(),
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "(1) 다항식을 정리한다").order(1).build(),
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "다음 식을 계산한다.").order(2).build(),
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "계산 결과를 확인한다.").order(3).build()))
                .build();

        String markdown = new NormalizedMarkdownRenderer().render(document, "");

        assertThat(markdown).contains("### 문제 12", "- (1) 다항식을 정리한다");
        assertThat(markdown).contains("다음 식을 계산한다.\n계산 결과를 확인한다.");
        assertThat(markdown).doesNotContain("다음 식을 계산한다.\n\n계산 결과를 확인한다.");
    }

    @Test
    void classifiesQuestionChoiceAndTableRolesForChunking() {
        ParsedFile parsed = new ParsedFile(DocumentFormat.PDF, "문제 12\n(1) 답", List.of(
                ParsedBlock.text("page[1]/block[0]", BlockType.PARAGRAPH, "문제 12", 1, 0,
                        Map.of("sourceRef", "page[1]/block[0]")),
                ParsedBlock.text("page[1]/block[1]", BlockType.PARAGRAPH, "(1) 답", 1, 1,
                        Map.of("sourceRef", "page[1]/block[1]"))),
                Map.of(), List.of(), List.of(), List.of(), List.of(), false);
        NormalizedDocument document = new ParsedFileNormalizedDocumentMapper().map(parsed,
                "doc-1", "sample.pdf", "pdf", "");

        assertThat(document.blocks()).extracting(block -> block.text() + ":" + block.metadata().get("contentRole"))
                .contains("문제 12:QUESTION", "(1) 답:CHOICE");
    }

    @Test
    void parsesPandocMarkdownAndStoresSnapshotResource() {
        DefaultMarkdownNormalizationPort port = new DefaultMarkdownNormalizationPort(
                new MarkdownTextBlockParser(), new NormalizedMarkdownRenderer(), objectMapper);

        var result = port.normalize(new MarkdownNormalizationPort.NormalizationRequest(
                "mrev-1", "html", "doc.html",
                "# Title\n\nParagraph\n\n- Item\n\n| A |\n| --- |\n| B |",
                List.of(), List.of(), NormalizedDocumentSnapshot.SOURCE_PANDOC));

        assertThat(result.markdown()).contains("# Title", "Paragraph", "- Item", "| A |");
        MarkdownResource resource = result.resources().stream()
                .filter(value -> MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT.equals(value.resourceType()))
                .findFirst()
                .orElseThrow();
        var snapshot = NormalizedDocumentSnapshot.read(resource, objectMapper).orElseThrow();
        assertThat(snapshot.normalizationStatus()).isEqualTo(NormalizedDocumentSnapshot.STATUS_VALID);
        assertThat(snapshot.normalizationSource()).isEqualTo(NormalizedDocumentSnapshot.SOURCE_PANDOC);
        assertThat(snapshot.qualityMetrics()).containsKeys("contentBlockCount", "searchablePageCoverage");
        assertThat(snapshot.document().blocks()).extracting(NormalizedBlock::type)
                .contains(NormalizedBlockType.TITLE, NormalizedBlockType.PARAGRAPH,
                        NormalizedBlockType.LIST_ITEM, NormalizedBlockType.TABLE);
    }

    @Test
    void recordsPdfRouteAnalysisAndQualityIssuesInSnapshotPayload() throws Exception {
        NormalizedDocument document = NormalizedDocument.builder("doc-1")
                .plainText("x^2 + 1 = 0")
                .metadata(Map.ofEntries(
                        Map.entry("pdfExtractionEngine", "pymupdf4llm"),
                        Map.entry("pdfRecommendedRoute", "MATH_DOCUMENT"),
                        Map.entry("pdfActualRoute", "PYMUPDF4LLM"),
                        Map.entry("pdfEngineSelectionReason", "MATH_DOCUMENT_ENGINE_DISABLED"),
                        Map.entry("recommendedRoute", "MATH_DOCUMENT"),
                        Map.entry("actualRoute", "OCR"),
                        Map.entry("mathOcrProvider", "pix2text"),
                        Map.entry("markdownQualityStatus", "REVIEW_REQUIRED"),
                        Map.entry("markdownQualityIssues", List.of("MATH_DOCUMENT_FALLBACK_USED")),
                        Map.entry("pdfAnalysis", Map.of(
                                "documentKind", "MATH_LIKE",
                                "ocrRecommended", false,
                                "textDensity", 0.2d,
                                "mojibakeScore", 0.0d)),
                        Map.entry("ocrApplied", false)))
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "x^2 + 1 = 0")
                        .order(0)
                        .build()))
                .build();
        List<String> issues = new NormalizedDocumentQualityValidator().validate(document, "x^2 + 1 = 0");

        MarkdownResource resource = NormalizedDocumentSnapshot.resource("mrev-1", document,
                NormalizedDocumentSnapshot.SOURCE_NATIVE, issues, objectMapper);
        Map<String, Object> payload = objectMapper.readValue(resource.metadataJson(), new TypeReference<>() {
        });

        assertThat(payload.get("normalizationStatus")).isEqualTo(NormalizedDocumentSnapshot.STATUS_REVIEW_REQUIRED);
        assertThat(payload.get("pdfRecommendedRoute")).isEqualTo("MATH_DOCUMENT");
        assertThat(payload.get("pdfActualRoute")).isEqualTo("PYMUPDF4LLM");
        assertThat(payload.get("recommendedRoute")).isEqualTo("MATH_DOCUMENT");
        assertThat(payload.get("actualRoute")).isEqualTo("OCR");
        assertThat(payload.get("mathOcrProvider")).isEqualTo("pix2text");
        assertThat(payload.get("markdownQualityStatus")).isEqualTo("REVIEW_REQUIRED");
        assertThat(payload.get("pdfEngineSelectionReason")).isEqualTo("MATH_DOCUMENT_ENGINE_DISABLED");
        assertThat(payload.get("normalizationIssues")).asList()
                .contains("MATH_DOCUMENT_REVIEW_REQUIRED", "MATH_NOT_RENDERED_AS_MARKDOWN",
                        "BLOCK_PROVENANCE_INCOMPLETE", "PAGE_SEARCHABILITY_REVIEW_REQUIRED",
                        "MATH_PAGE_PROVENANCE_REVIEW_REQUIRED");
        assertThat(payload.get("pdfAnalysis")).isInstanceOf(Map.class);
        assertThat(payload.get("contentBlockCount")).isEqualTo(1);
        assertThat(payload.get("pageProvenanceBlockCount")).isEqualTo(0);
        assertThat((Double) payload.get("pageProvenanceCoverage")).isEqualTo(0.0d);
        assertThat(payload.get("mathBlockCount")).isEqualTo(1);
        assertThat((Double) payload.get("mathPageProvenanceCoverage")).isEqualTo(0.0d);
    }

    @Test
    void snapshotFillsClientRouteAliasesFromPdfRouteMetadata() throws Exception {
        NormalizedDocument document = NormalizedDocument.builder("doc-1")
                .metadata(Map.of(
                        "pdfRecommendedRoute", "MATH_DOCUMENT",
                        "pdfActualRoute", "OCR",
                        "pdfEngineSelectionReason", "MATH_DOCUMENT_HYBRID_SELECTED"))
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "$x^2$")
                        .page(1)
                        .sourceRef("page[1]/block[0]")
                        .order(0)
                        .build()))
                .build();

        MarkdownResource resource = NormalizedDocumentSnapshot.resource("mrev-1", document,
                NormalizedDocumentSnapshot.SOURCE_NATIVE, List.of(), objectMapper);
        Map<String, Object> payload = objectMapper.readValue(resource.metadataJson(), new TypeReference<>() {
        });

        assertThat(payload.get("recommendedRoute")).isEqualTo("MATH_DOCUMENT");
        assertThat(payload.get("actualRoute")).isEqualTo("OCR");
        assertThat(payload.get("engineSelectionReason")).isEqualTo("MATH_DOCUMENT_HYBRID_SELECTED");
        assertThat(payload.get("markdownQualityStatus")).isEqualTo("VALID");
        assertThat(payload.get("pageProvenanceStatus")).isEqualTo("VALID");
    }

    @Test
    void flagsNoisyHeuristicMathOcrAsReviewRequired() {
        NormalizedDocument document = NormalizedDocument.builder("doc-ocr")
                .plainText("2x°+3x-4=0")
                .metadata(Map.of(
                        "pdfRecommendedRoute", "MATH_DOCUMENT",
                        "pdfActualRoute", "MATH_DOCUMENT",
                        "mathMarkdownApplied", true,
                        "pdfAnalysis", Map.of(
                                "documentKind", "MIXED",
                                "ocrRecommended", true,
                                "textDensity", 0.0d,
                                "mojibakeScore", 0.0d),
                        "ocrApplied", true))
                .blocks(List.of(
                        NormalizedBlock.builder(NormalizedBlockType.OCR_TEXT, "i ss NOS").order(0).build(),
                        NormalizedBlock.builder(NormalizedBlockType.OCR_TEXT, "$2x^{2}+3x-4=0$").order(1).build(),
                        NormalizedBlock.builder(NormalizedBlockType.OCR_TEXT, "고대.그리스의수학자유클리드").order(2)
                                .build()))
                .build();

        List<String> issues = new NormalizedDocumentQualityValidator().validate(document,
                "i ss NOS\n$2x^{2}+3x-4=0$\n고대.그리스의수학자유클리드");

        assertThat(issues).contains("OCR_BLOCKS_NOT_RECLASSIFIED", "MATH_MARKDOWN_HEURISTIC_ONLY",
                "KOREAN_SPACING_REVIEW_REQUIRED");
    }

    @Test
    void flagsFragmentedBrokenLatexAndMissingPageProvenance() {
        NormalizedDocument document = NormalizedDocument.builder("doc-ocr")
                .metadata(Map.of(
                        "pdfExtractionEngine", "pix2text",
                        "pdfRecommendedRoute", "MATH_DOCUMENT",
                        "pdfActualRoute", "MATH_DOCUMENT",
                        "pdfAnalysis", Map.of("documentKind", "MATH_LIKE"),
                        "ocrApplied", true))
                .blocks(List.of(
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "리").order(0).build(),
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "$x^2+1$\"").order(1).build()))
                .build();

        List<String> issues = new NormalizedDocumentQualityValidator().validate(document,
                "리\n정\nOO\nSS\n$x^2+1$\"");

        assertThat(issues).contains("FRAGMENTED_SHORT_LINES", "BROKEN_LATEX_DELIMITER",
                "PAGE_PROVENANCE_INCOMPLETE", "PAGE_SEARCHABILITY_REVIEW_REQUIRED",
                "MATH_PAGE_PROVENANCE_REVIEW_REQUIRED");
    }

    @Test
    void blocksRagIndexWhenKoreanJamoAndMathRenderingLossAreSevere() {
        List<NormalizedBlock> blocks = new java.util.ArrayList<>();
        for (int index = 0; index < 24; index++) {
            blocks.add(NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "x^2+" + index + "=0")
                    .page(1)
                    .sourceRef("page[1]/block[" + index + "]")
                    .order(index)
                    .build());
        }
        NormalizedDocument document = NormalizedDocument.builder("doc-quality")
                .metadata(Map.of(
                        "fallbackApplied", true,
                        "fallbackFrom", "pymupdf4llm",
                        "pdfRecommendedRoute", "MATH_DOCUMENT",
                        "pdfActualRoute", "OCR",
                        "pdfAnalysis", Map.of("documentKind", "MATH_LIKE", "pageCount", 2)))
                .blocks(blocks)
                .build();
        String markdown = "다항식의 ㅠ산 결과를 구한다.\n일반 본문만 남았다.";
        NormalizedDocumentQualityValidator validator = new NormalizedDocumentQualityValidator();
        List<String> issues = validator.validate(document, markdown);
        NormalizedDocument assessed = new RenderedMarkdownPostProcessor()
                .withRenderedQuality(document, markdown, issues);

        assertThat(issues).contains("KOREAN_JAMO_REVIEW_REQUIRED", "PYMUPDF_FALLBACK_USED",
                "MATH_RENDERING_LOSS", "CONTENT_PAGE_COVERAGE_INCOMPLETE");
        assertThat(assessed.metadata())
                .containsEntry("ragIndexEligible", false)
                .containsEntry("qualityGateStatus", "BLOCKED");
        assertThat((Double) assessed.metadata().get("markdownQualityScore")).isLessThan(0.85d);
    }

    @Test
    void blocksRagIndexWhenKoreanDocumentWasTransliteratedToLatin() {
        String garbled = "CrefAlo ChStAJo Cerio HUH GES latin replacement text ".repeat(12);
        NormalizedDocument document = NormalizedDocument.builder("doc-korean-garbling")
                .filename("미래엔_고등수학.pdf")
                .metadata(Map.of("ocrLanguage", "kor+eng", "pdfExtractionEngine", "pymupdf4llm"))
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, garbled)
                        .page(1)
                        .sourceRef("page[1]/block[0]")
                        .order(0)
                        .build()))
                .build();
        NormalizedDocumentQualityValidator validator = new NormalizedDocumentQualityValidator();
        List<String> issues = validator.validate(document, garbled);
        NormalizedDocument assessed = new RenderedMarkdownPostProcessor()
                .withRenderedQuality(document, garbled, issues);

        assertThat(issues).contains("KOREAN_TEXT_GARBLING");
        assertThat(assessed.metadata())
                .containsEntry("ragIndexEligible", false)
                .containsEntry("qualityGateStatus", "BLOCKED");
    }
}
