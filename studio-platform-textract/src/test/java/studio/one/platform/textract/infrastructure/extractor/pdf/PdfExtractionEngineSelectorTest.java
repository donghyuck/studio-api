package studio.one.platform.textract.infrastructure.extractor.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import studio.one.platform.textract.domain.model.DocumentFormat;
import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.ParsedFile;

class PdfExtractionEngineSelectorTest {

    @Test
    void autoUsesPdfBoxWhenWorkerIsDisabled() {
        ParsedFile result = selector(pdfBox("pdfbox text")).extract(request(PdfExtractionOptions.defaults()));

        assertThat(result.plainText()).isEqualTo("pdfbox text");
        assertThat(result.metadata()).containsEntry(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pdfbox");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void explicitPyMuPdfFallsBackToPdfBoxWhenFallbackIsEnabled() {
        PdfExtractionOptions options = new PdfExtractionOptions(
                PdfExtractionMode.PYMUPDF4LLM,
                true,
                true,
                true,
                false,
                false,
                false,
                null,
                3,
                1024);

        ParsedFile result = selector(failingPyMuPdf(), pdfBox("fallback text")).extract(request(options));

        assertThat(result.plainText()).isEqualTo("fallback text");
        assertThat(result.metadata())
                .containsEntry(PdfExtractionEngineSelector.KEY_FALLBACK_FROM, "pymupdf4llm")
                .containsEntry(PdfExtractionEngineSelector.KEY_PYMUPDF_STATUS, "FAILED")
                .containsEntry(PdfExtractionEngineSelector.KEY_FALLBACK_REASON, "PYMUPDF4LLM_FAILED")
                .containsEntry("fallbackApplied", true)
                .containsKey(PdfExtractionEngineSelector.KEY_PYMUPDF_ERROR);
        assertThat(result.warnings()).extracting(warning -> warning.canonicalCode())
                .contains("PYMUPDF4LLM_FAILED");
    }

    @Test
    void fallbackDiagnosticsDoNotPersistWorkerResponseBody() {
        PdfExtractionEngine failing = new PdfExtractionEngine() {
            @Override
            public PdfExtractionEngineType type() {
                return PdfExtractionEngineType.PYMUPDF4LLM;
            }

            @Override
            public boolean supports(PdfExtractionRequest request) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request) {
                throw new FileParseException("Worker returned HTTP 500 body={\"documentText\":\"secret\"}");
            }
        };

        PdfExtractionOptions options = new PdfExtractionOptions(
                PdfExtractionMode.PYMUPDF4LLM,
                true,
                true,
                true,
                false,
                false,
                false,
                null,
                3,
                1024);
        ParsedFile result = selector(failing, pdfBox("fallback text")).extract(request(options));

        assertThat(result.metadata().get(PdfExtractionEngineSelector.KEY_PYMUPDF_ERROR).toString())
                .contains("HTTP 500")
                .doesNotContain("documentText")
                .doesNotContain("secret");
    }

    @Test
    void explicitPyMuPdfPropagatesFailureWhenFallbackIsDisabled() {
        PdfExtractionOptions options = new PdfExtractionOptions(
                PdfExtractionMode.PYMUPDF4LLM,
                false,
                true,
                true,
                false,
                false,
                false,
                null,
                3,
                1024);

        assertThatThrownBy(() -> selector(failingPyMuPdf(), pdfBox("fallback text")).extract(request(options)))
                .isInstanceOf(FileParseException.class)
                .hasMessageContaining("worker failed");
    }

    @Test
    void autoPrefersPyMuPdfWhenPageCountMatchesThreshold() {
        PdfExtractionOptions options = new PdfExtractionOptions(
                PdfExtractionMode.AUTO,
                true,
                true,
                true,
                false,
                false,
                false,
                3,
                3,
                1024);

        ParsedFile result = selector(pdfBox("pdfbox text"), pyMuPdf("markdown text")).extract(request(options));

        assertThat(result.plainText()).isEqualTo("markdown text");
        assertThat(result.metadata())
                .containsEntry(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm")
                .containsEntry(PdfExtractionEngineSelector.KEY_PYMUPDF_STATUS, "ACCEPTED")
                .containsEntry("baselineEngine", "pymupdf4llm");
    }

    @Test
    void autoUsesPyMuPdfWhenPdfBoxIsDisabled() {
        PdfExtractionOptions options = new PdfExtractionOptions(
                PdfExtractionMode.AUTO,
                true,
                false,
                true,
                false,
                false,
                false,
                null,
                3,
                1024);

        ParsedFile result = selector(pyMuPdf("markdown text")).extract(request(options));

        assertThat(result.plainText()).isEqualTo("markdown text");
        assertThat(result.metadata()).containsEntry(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm");
    }

    @Test
    void supportsRespectsExplicitPdfBoxSelection() {
        PdfExtractionOptions options = new PdfExtractionOptions(
                PdfExtractionMode.PDFBOX,
                true,
                false,
                true,
                false,
                false,
                false,
                null,
                3,
                1024);

        boolean supported = selector(pyMuPdf("markdown text")).supports(request(options));

        assertThat(supported).isFalse();
    }

    @Test
    void autoRecordsMathRecommendationWithoutCallingMathEngineUntilClientForcesOcr() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                12, 2, 0.3d, 0.0d, 0.0d, false, 0.0d, 0.5d, 0.2d, PdfDocumentKind.MATH_LIKE);
        MathDocumentExtractionEngine mathEngine = mathEngine("math text", "pix2text");
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis, List.of(mathEngine),
                pdfBox("pdfbox text"), pyMuPdf("markdown text"));

        ParsedFile result = selector.extract(request(PdfExtractionOptions.defaults()));

        assertThat(result.plainText()).isEqualTo("markdown text");
        assertThat(result.metadata())
                .containsEntry(PdfExtractionEngineSelector.KEY_RECOMMENDED_ROUTE, "MATH_DOCUMENT")
                .containsEntry(PdfExtractionEngineSelector.KEY_ACTUAL_ROUTE, "PYMUPDF4LLM")
                .containsEntry(PdfExtractionEngineSelector.KEY_ENGINE_SELECTION_REASON,
                        "MATH_DOCUMENT_REQUIRES_CLIENT_FORCE")
                .containsEntry(PdfExtractionEngineSelector.KEY_MATH_FALLBACK_APPLIED, false)
                .containsEntry(PdfExtractionEngineSelector.KEY_OCR_REQUESTED_BY, "NONE");
        assertThat(result.metadata().get(PdfExtractionEngineSelector.KEY_ANALYSIS)).isInstanceOf(Map.class);
        assertThat(result.warnings()).extracting(warning -> warning.canonicalCode())
                .contains("MATH_DOCUMENT_REQUIRES_CLIENT_FORCE");
    }

    @Test
    void autoUsesEnabledMathEngineWhenClientForcesOcr() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                12, 2, 0.3d, 0.0d, 0.0d, false, 0.0d, 0.5d, 0.2d, PdfDocumentKind.MATH_LIKE);
        MathDocumentExtractionEngine mathEngine = new MathDocumentExtractionEngine() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public boolean supports(PdfExtractionRequest request, PdfDocumentAnalysis analysis) {
                return analysis.mathLike();
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis analysis) {
                return ParsedFile.textOnly(DocumentFormat.PDF, "math text", request.filename());
            }
        };
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis, List.of(mathEngine),
                pdfBox("pdfbox text"), pyMuPdf("markdown text"));

        ParsedFile result = selector.extract(request(PdfExtractionOptions.defaults().withOcrMode("FORCE")));

        assertThat(result.plainText()).isEqualTo("math text");
        assertThat(result.metadata())
                .containsEntry(PdfExtractionEngineSelector.KEY_RECOMMENDED_ROUTE, "MATH_DOCUMENT")
                .containsEntry(PdfExtractionEngineSelector.KEY_ACTUAL_ROUTE, "MATH_DOCUMENT")
                .containsEntry(PdfExtractionEngineSelector.KEY_OCR_REQUESTED_BY, "CLIENT");
    }

    @Test
    void autoUsesMathEngineForHighMathSignalWhenClientForcesOcr() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                44, 5, 0.0d, 1.0d, 0.0d, true, 0.0d, 0.0d, 1.0d, PdfDocumentKind.SCANNED);
        MathDocumentExtractionEngine mathEngine = mathEngine("## 수식\n\n$x^{2}+3x+2=0$", "pix2text");
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis, List.of(mathEngine),
                pdfBox("pdfbox text"), pyMuPdf("markdown text"));

        ParsedFile result = selector.extract(request(PdfExtractionOptions.defaults().withOcrMode("FORCE")));

        assertThat(result.markdown()).contains("$x^{2}+3x+2=0$");
        assertThat(result.metadata())
                .containsEntry(PdfExtractionEngineSelector.KEY_RECOMMENDED_ROUTE, "MATH_DOCUMENT")
                .containsEntry(PdfExtractionEngineSelector.KEY_ACTUAL_ROUTE, "MATH_DOCUMENT")
                .containsEntry("mathOcrProvider", "pix2text");
    }

    @Test
    void autoFallsBackToNextMathEngineWhenQualityGateRejectsFirstResult() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                12, 2, 0.3d, 0.0d, 0.0d, false, 0.0d, 0.5d, 0.2d, PdfDocumentKind.MATH_LIKE);
        MathDocumentExtractionEngine noisyEngine = mathEngine("OO\nSS\nSAS\n리\n정\n$x^2+1$\"", "pix2text");
        MathDocumentExtractionEngine cleanEngine = mathEngine("## 다항식\n\n$a^{2}-2ab+b^{2}$", "mathpix");
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis, List.of(noisyEngine, cleanEngine),
                pdfBox("pdfbox text"), pyMuPdf("markdown text"));

        ParsedFile result = selector.extract(request(PdfExtractionOptions.defaults().withOcrMode("FORCE")));

        assertThat(result.markdown()).contains("$a^{2}-2ab+b^{2}$");
        assertThat(result.metadata())
                .containsEntry("mathOcrProvider", "mathpix")
                .containsEntry(PdfExtractionEngineSelector.KEY_MATH_FALLBACK_APPLIED, true)
                .containsEntry(PdfExtractionEngineSelector.KEY_PAGE_PROVENANCE_STATUS, "VALID");
        assertThat(result.warnings()).extracting(warning -> warning.canonicalCode())
                .contains("MATH_MARKDOWN_QUALITY_GATE_FAILED");
    }

    @Test
    void autoMathEngineCanPostProcessMarkdownMath() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                12, 2, 0.3d, 0.0d, 0.0d, false, 0.0d, 0.5d, 0.2d, PdfDocumentKind.MATH_LIKE);
        PdfExtractionEngine delegate = pyMuPdf("2x^2+3x-4=0");
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis,
                List.of(new HeuristicMathDocumentExtractionEngine(delegate)), pdfBox("pdfbox text"), delegate);

        ParsedFile result = selector.extract(request(PdfExtractionOptions.defaults().withOcrMode("FORCE")));

        assertThat(result.markdown()).contains("$2x^{2}+3x-4=0$");
        assertThat(result.metadata())
                .containsEntry(PdfExtractionEngineSelector.KEY_RECOMMENDED_ROUTE, "MATH_DOCUMENT")
                .containsEntry(PdfExtractionEngineSelector.KEY_ACTUAL_ROUTE, "MATH_DOCUMENT")
                .containsEntry(MathMarkdownPostProcessor.KEY_MATH_MARKDOWN_APPLIED, true);
    }

    @Test
    void explicitPyMuPdfWithOcrUsesMathEngineForMathLikeDocuments() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                44, 5, 0.0d, 1.0d, 0.0d, true, 0.0d, 1.0d, 0.0d, PdfDocumentKind.MIXED);
        AtomicReference<PdfExtractionRequest> captured = new AtomicReference<>();
        PdfExtractionEngine delegate = new PdfExtractionEngine() {
            @Override
            public PdfExtractionEngineType type() {
                return PdfExtractionEngineType.PYMUPDF4LLM;
            }

            @Override
            public boolean supports(PdfExtractionRequest request) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request) {
                captured.set(request);
                ParsedFile file = ParsedFile.textOnly(DocumentFormat.PDF, "2x^2+3x-4=0", request.filename());
                return new ParsedFile(
                        file.format(),
                        file.plainText(),
                        file.blocks(),
                        Map.of(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm"),
                        file.warnings(),
                        file.pages(),
                        file.tables(),
                        file.images(),
                        true,
                        file.plainText(),
                        "markdown",
                        file.locators());
            }
        };
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis,
                List.of(new HeuristicMathDocumentExtractionEngine(delegate)), pdfBox("pdfbox text"), delegate);
        PdfExtractionOptions options = new PdfExtractionOptions(
                PdfExtractionMode.PYMUPDF4LLM,
                true,
                true,
                true,
                true,
                false,
                false,
                null,
                3,
                1024)
                .withOcrLanguage("kor+eng");

        ParsedFile result = selector.extract(request(options));

        assertThat(captured.get().options().ocrRequired()).isTrue();
        assertThat(captured.get().options().ocrLanguage()).isEqualTo("kor+eng");
        assertThat(result.markdown()).contains("$2x^{2}+3x-4=0$");
        assertThat(result.metadata())
                .containsEntry(PdfExtractionEngineSelector.KEY_RECOMMENDED_ROUTE, "MATH_DOCUMENT")
                .containsEntry(PdfExtractionEngineSelector.KEY_ACTUAL_ROUTE, "MATH_DOCUMENT")
                .containsEntry(PdfExtractionEngineSelector.KEY_ENGINE_SELECTION_REASON,
                        "EXPLICIT_PYMUPDF4LLM_WITH_OCR_MATH_DOCUMENT")
                .containsEntry(MathMarkdownPostProcessor.KEY_MATH_MARKDOWN_APPLIED, true);
    }

    @Test
    void hybridMathRouteKeepsFullBaselineAndLimitsMathOcrPages() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                44, 5, 0.0d, 1.0d, 0.0d, true, 0.0d, 1.0d, 1.0d, PdfDocumentKind.MATH_LIKE);
        AtomicReference<PdfExtractionRequest> baselineRequest = new AtomicReference<>();
        List<Integer> mathPages = new java.util.ArrayList<>();
        PdfExtractionEngine baseline = new PdfExtractionEngine() {
            @Override
            public PdfExtractionEngineType type() {
                return PdfExtractionEngineType.PYMUPDF4LLM;
            }

            @Override
            public boolean supports(PdfExtractionRequest request) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request) {
                baselineRequest.set(request);
                ParsedFile file = ParsedFile.textOnly(DocumentFormat.PDF, "baseline all pages", request.filename());
                return new ParsedFile(
                        file.format(),
                        file.plainText(),
                        file.blocks(),
                        Map.of(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm"),
                        file.warnings(),
                        file.pages(),
                        file.tables(),
                        file.images(),
                        true,
                        file.plainText(),
                        "markdown",
                        file.locators());
            }
        };
        MathDocumentExtractionEngine mathEngine = new MathDocumentExtractionEngine() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public boolean supports(PdfExtractionRequest request, PdfDocumentAnalysis analysis) {
                return analysis.mathLike();
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis analysis) {
                mathPages.add(request.options().pageFrom());
                String markdown = """
                        OO
                        社就台各洲人早台才各州划公WIR
                        $x_%s^{2}$
                        """.formatted("{" + request.options().pageFrom() + "}");
                return new ParsedFile(
                        DocumentFormat.PDF,
                        markdown,
                        List.of(studio.one.platform.textract.domain.model.ParsedBlock.text(
                                "pix2text/page[" + request.options().pageFrom() + "]",
                                studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                                markdown,
                                request.options().pageFrom(),
                                0,
                                Map.of("sourceRef", "page[" + request.options().pageFrom() + "]/pix2text-block[0]",
                                        "page", request.options().pageFrom()))),
                        Map.of(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pix2text",
                                "mathOcrProvider", "pix2text",
                                "pageFrom", request.options().pageFrom(),
                                "pageTo", request.options().pageTo()),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        true,
                        markdown,
                        "markdown",
                        List.of());
            }
        };
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis, List.of(mathEngine), true, 4,
                pdfBox("pdfbox text"), baseline);

        ParsedFile result = selector.extract(request(PdfExtractionOptions.defaults().withOcrMode("FORCE")));

        assertThat(baselineRequest.get().options().ocrRequired()).isFalse();
        assertThat(baselineRequest.get().options().ocrMode()).isEqualTo("AUTO");
        assertThat(mathPages).containsExactly(1, 2);
        assertThat(result.markdown()).contains("baseline all pages")
                .doesNotContain("Math OCR Supplement", "$x_{44}^{2}$");
        assertThat(result.blocks()).hasSize(3);
        assertThat(result.blocks()).extracting(studio.one.platform.textract.domain.model.ParsedBlock::text)
                .contains("$x_{1}^{2}$", "$x_{2}^{2}$");
        assertThat(result.metadata())
                .containsEntry(PdfExtractionEngineSelector.KEY_RECOMMENDED_ROUTE, "MATH_DOCUMENT")
                .containsEntry(PdfExtractionEngineSelector.KEY_ACTUAL_ROUTE, "OCR")
                .containsEntry("mathHybridApplied", true)
                .containsEntry("mathHybridSupplementPageCount", 2)
                .containsEntry("mathHybridFormulaBlockCount", 2)
                .containsEntry("mathCorrectionPages", List.of(1, 2))
                .containsEntry("mathCorrectionPageSelection", "FALLBACK_SAMPLE");
    }

    @Test
    void hybridMathRouteSkipsFailedSupplementEngineForRemainingPages() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                3, 0, 0.0d, 0.0d, 0.0d, false, 0.0d, 0.0d, 1.0d, PdfDocumentKind.MATH_LIKE);
        AtomicInteger failedCalls = new AtomicInteger();
        AtomicInteger succeedingCalls = new AtomicInteger();
        MathDocumentExtractionEngine failingEngine = new MathDocumentExtractionEngine() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public boolean supports(PdfExtractionRequest request, PdfDocumentAnalysis ignored) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis ignored) throws FileParseException {
                failedCalls.incrementAndGet();
                throw new FileParseException("supplement worker unavailable");
            }
        };
        MathDocumentExtractionEngine succeedingEngine = new MathDocumentExtractionEngine() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public boolean supports(PdfExtractionRequest request, PdfDocumentAnalysis ignored) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis ignored) {
                succeedingCalls.incrementAndGet();
                int page = request.options().pageFrom();
                return ParsedFile.textOnly(DocumentFormat.PDF, "$x_" + page + "$", request.filename());
            }
        };
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis,
                List.of(failingEngine, succeedingEngine), true, 3, pdfBox("fallback"), pyMuPdf("baseline"));

        selector.extract(request(PdfExtractionOptions.defaults().withOcrMode("FORCE")));

        assertThat(failedCalls).hasValue(1);
        assertThat(succeedingCalls).hasValue(2);
    }

    @Test
    void hybridMathRouteCanAddVisionCorrectionFormulaBlocks() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                12, 5, 0.0d, 1.0d, 0.0d, true, 0.0d, 1.0d, 1.0d, PdfDocumentKind.MATH_LIKE);
        PdfExtractionEngine baseline = pyMuPdf("baseline all pages");
        MathDocumentExtractionEngine mathEngine = mathEngine("$x^{2}+1$", "pix2text");
        MathVisionCorrectionClient visionClient = new MathVisionCorrectionClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String provider() {
                return "gemini";
            }

            @Override
            public ParsedFile correct(PdfExtractionRequest request, PdfDocumentAnalysis analysis, List<Integer> pages) {
                return new ParsedFile(
                        DocumentFormat.PDF,
                        "$a^{2}-b^{2}$",
                        List.of(studio.one.platform.textract.domain.model.ParsedBlock.text(
                                "math-vision/page[1]/formula[0]",
                                studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                                "$a^{2}-b^{2}$",
                                1,
                                0,
                                Map.of("sourceRef", "math-vision/page[1]/formula[0]"))),
                        Map.of("mathVisionProvider", "gemini"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        true,
                        "$a^{2}-b^{2}$",
                        "markdown",
                        List.of());
            }
        };
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis, List.of(mathEngine), true, 2,
                List.of(visionClient), true, pdfBox("pdfbox text"), baseline);

        ParsedFile result = selector.extract(request(PdfExtractionOptions.defaults()
                .withOcrMode("FORCE")
                .withMathVisionCorrection(true)));

        assertThat(result.blocks()).extracting(studio.one.platform.textract.domain.model.ParsedBlock::text)
                .contains("$a^{2}-b^{2}$");
        assertThat(result.metadata())
                .containsEntry("mathVisionCorrectionApplied", true)
                .containsEntry("mathVisionCorrectionRequested", true)
                .containsEntry("mathVisionCorrectionProvider", "gemini")
                .containsEntry("mathVisionFormulaBlockCount", 1)
                .containsEntry("mathVisionCorrectionPageCount", 2)
                .containsEntry("mathVisionCorrectionBatchCount", 1)
                .containsEntry("mathVisionCorrectionFailedBatchCount", 0);
    }

    @Test
    void hybridMathRouteUsesKoreanOcrForAllRequestedPagesWhenBaselineIsLatinGarbling() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                3, 3, 0.0d, 1.0d, 0.0d, true, 0.0d, 1.0d, 1.0d, PdfDocumentKind.MATH_LIKE);
        String garbled = "CrefAlo ChStAJo Cerio HUH GES latin replacement text ".repeat(12);
        PdfExtractionEngine baseline = new PdfExtractionEngine() {
            @Override
            public PdfExtractionEngineType type() {
                return PdfExtractionEngineType.PYMUPDF4LLM;
            }

            @Override
            public boolean supports(PdfExtractionRequest request) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request) {
                List<studio.one.platform.textract.domain.model.ParsedBlock> blocks = new ArrayList<>();
                for (int page = 1; page <= 3; page++) {
                    blocks.add(studio.one.platform.textract.domain.model.ParsedBlock.text(
                            "page[" + page + "]/block[0]",
                            studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                            garbled,
                            page,
                            page - 1,
                            Map.of("sourceRef", "page[" + page + "]/block[0]")));
                }
                return new ParsedFile(DocumentFormat.PDF, garbled, blocks,
                        Map.of(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm"),
                        List.of(), List.of(), List.of(), List.of(), false,
                        garbled, "markdown", List.of());
            }
        };
        List<Integer> correctedPages = new ArrayList<>();
        AtomicInteger koreanOcrCalls = new AtomicInteger();
        KoreanTextOcrClient koreanClient = new KoreanTextOcrClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String provider() {
                return "paddleocr";
            }

            @Override
            public int maxPagesPerRequest() {
                return 8;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis ignored) {
                koreanOcrCalls.incrementAndGet();
                String text = "다항식의 연산과 계산 원리를 설명한다. ".repeat(4);
                List<studio.one.platform.textract.domain.model.ParsedBlock> blocks = new ArrayList<>();
                for (int page = request.options().pageFrom(); page <= request.options().pageTo(); page++) {
                    correctedPages.add(page);
                    blocks.add(studio.one.platform.textract.domain.model.ParsedBlock.text(
                            "page[" + page + "]/block[0]",
                            studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                            text, page, page - 1, Map.of("sourceRef", "page[" + page + "]/block[0]")));
                }
                return new ParsedFile(DocumentFormat.PDF, text,
                        blocks,
                        Map.of(), List.of(), List.of(), List.of(), List.of(), true);
            }
        };
        PdfExtractionEngineSelector selector = new PdfExtractionEngineSelector(
                List.of(baseline),
                new PdfDocumentAnalyzer() {
                    @Override
                    public PdfDocumentAnalysis analyze(PdfExtractionRequest request) {
                        return analysis;
                    }
                },
                List.of(mathEngine("$x^{2}+1$", "pix2text")),
                true, 0.65d, true, 2, List.of(), false,
                List.of(koreanClient), 3,
                new PdfExtractionEngineSelector.MathCorrectionPolicy(2, 2, 2, java.time.Duration.ofMinutes(1)));
        PdfExtractionOptions options = PdfExtractionOptions.defaults()
                .withOcrMode("FORCE")
                .withOcrLanguage("kor+eng");
        PdfExtractionRequest request = new PdfExtractionRequest(
                new byte[] {1}, "application/pdf", "미래엔_고등수학.pdf", options);

        ParsedFile result = selector.extract(request);

        assertThat(correctedPages).containsExactly(1, 2, 3);
        assertThat(koreanOcrCalls).hasValue(1);
        assertThat(result.metadata())
                .containsEntry("koreanTextOcrApplied", true)
                .containsEntry("koreanTextOcrPages", List.of(1, 2, 3))
                .containsEntry("koreanTextOcrRequestedPages", List.of(1, 2, 3))
                .containsEntry("koreanTextOcrMissingPages", List.of())
                .containsEntry("koreanTextOcrComplete", true);
        assertThat(result.blocks()).anyMatch(block -> Boolean.TRUE.equals(
                block.metadata().get("catastrophicKoreanBaseline")));
    }

    @Test
    void hybridMathRouteFailsWhenCatastrophicKoreanBaselineIsOnlyPartiallyCorrected() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                3, 3, 0.0d, 1.0d, 0.0d, true, 0.0d, 1.0d, 1.0d, PdfDocumentKind.MATH_LIKE);
        String garbled = "CrefAlo ChStAJo Cerio HUH GES latin replacement text ".repeat(12);
        KoreanTextOcrClient partialClient = new KoreanTextOcrClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String provider() {
                return "paddleocr";
            }

            @Override
            public int maxPagesPerRequest() {
                return 8;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis ignored) {
                String text = "다항식의 연산과 계산 원리를 설명한다.";
                return new ParsedFile(DocumentFormat.PDF, text,
                        List.of(studio.one.platform.textract.domain.model.ParsedBlock.text(
                                "page[1]/block[0]",
                                studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                                text, 1, 0, Map.of("sourceRef", "page[1]/block[0]"))),
                        Map.of(), List.of(), List.of(), List.of(), List.of(), true);
            }
        };
        PdfExtractionEngineSelector selector = new PdfExtractionEngineSelector(
                List.of(garbledPyMuPdf(3, garbled)),
                new PdfDocumentAnalyzer() {
                    @Override
                    public PdfDocumentAnalysis analyze(PdfExtractionRequest request) {
                        return analysis;
                    }
                },
                List.of(mathEngine("$x^{2}+1$", "pix2text")),
                true, 0.65d, true, 2, List.of(), false,
                List.of(partialClient), 3,
                new PdfExtractionEngineSelector.MathCorrectionPolicy(2, 2, 2,
                        java.time.Duration.ofMinutes(1)));
        PdfExtractionOptions options = PdfExtractionOptions.defaults()
                .withOcrMode("FORCE")
                .withOcrLanguage("kor+eng");
        PdfExtractionRequest request = new PdfExtractionRequest(
                new byte[] {1}, "application/pdf", "미래엔_고등수학.pdf", options);

        assertThatThrownBy(() -> selector.extract(request))
                .isInstanceOf(FileParseException.class)
                .hasMessageContaining("all required pages");
    }

    @Test
    void hybridMathRouteUsesFallbackKoreanOcrWhenPrimaryProviderFails() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                2, 2, 0.0d, 1.0d, 0.0d, true, 0.0d, 1.0d, 1.0d, PdfDocumentKind.MATH_LIKE);
        String garbled = "CrefAlo ChStAJo Cerio HUH GES latin replacement text ".repeat(12);
        AtomicInteger primaryCalls = new AtomicInteger();
        KoreanTextOcrClient primary = koreanOcrClient("paddleocr", request -> {
            primaryCalls.incrementAndGet();
            throw new FileParseException("worker unavailable");
        });
        KoreanTextOcrClient fallback = koreanOcrClient("pymupdf4llm-korean-ocr", request -> {
            String text = "다항식의 연산과 계산 원리를 설명한다.";
            List<studio.one.platform.textract.domain.model.ParsedBlock> blocks = new ArrayList<>();
            for (int page = request.options().pageFrom(); page <= request.options().pageTo(); page++) {
                blocks.add(studio.one.platform.textract.domain.model.ParsedBlock.text(
                        "page[" + page + "]/block[0]",
                        studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                        text, page, page - 1, Map.of("sourceRef", "page[" + page + "]/block[0]")));
            }
            return new ParsedFile(DocumentFormat.PDF, text, blocks,
                    Map.of(), List.of(), List.of(), List.of(), List.of(), true);
        });
        PdfExtractionEngineSelector selector = new PdfExtractionEngineSelector(
                List.of(garbledPyMuPdf(2, garbled)), new PdfDocumentAnalyzer() {
                    @Override
                    public PdfDocumentAnalysis analyze(PdfExtractionRequest request) {
                        return analysis;
                    }
                },
                List.of(mathEngine("$x^{2}+1$", "pix2text")),
                true, 0.65d, true, 2, List.of(), false,
                List.of(primary, fallback), 2,
                new PdfExtractionEngineSelector.MathCorrectionPolicy(2, 2, 2,
                        java.time.Duration.ofMinutes(1)));

        ParsedFile result = selector.extract(new PdfExtractionRequest(
                new byte[] {1}, "application/pdf", "미래엔_고등수학.pdf",
                PdfExtractionOptions.defaults().withOcrMode("FORCE").withOcrLanguage("kor+eng")));

        assertThat(primaryCalls).hasValue(1);
        assertThat(result.metadata())
                .containsEntry("koreanTextOcrApplied", true)
                .containsEntry("koreanTextOcrProvider", "pymupdf4llm-korean-ocr")
                .containsEntry("koreanTextOcrComplete", true);
    }

    @Test
    void hybridMathRoutePreservesPrimaryPagesAndFallsBackOnlyForMissingPages() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                4, 4, 0.0d, 1.0d, 0.0d, true, 0.0d, 1.0d, 1.0d, PdfDocumentKind.MATH_LIKE);
        String garbled = "CrefAlo ChStAJo Cerio HUH GES latin replacement text ".repeat(12);
        List<String> requests = new ArrayList<>();
        KoreanTextOcrClient primary = batchedKoreanOcrClient("paddleocr", 2, request -> {
            requests.add("primary:" + request.options().pageFrom() + "-" + request.options().pageTo());
            if (request.options().pageFrom() == 3) {
                throw new FileParseException("late batch timeout");
            }
            return koreanCorrection(request, "원격 OCR로 복원한 다항식 본문이다.");
        });
        KoreanTextOcrClient fallback = batchedKoreanOcrClient("pymupdf4llm-korean-ocr", 2, request -> {
            requests.add("fallback:" + request.options().pageFrom() + "-" + request.options().pageTo());
            return koreanCorrection(request, "대체 OCR로 복원한 다항식 본문이다.");
        });
        PdfExtractionEngineSelector selector = new PdfExtractionEngineSelector(
                List.of(garbledPyMuPdf(4, garbled)), new PdfDocumentAnalyzer() {
                    @Override
                    public PdfDocumentAnalysis analyze(PdfExtractionRequest request) {
                        return analysis;
                    }
                },
                List.of(mathEngine("$x^2+1$", "pix2text")),
                true, 0.65d, true, 2, List.of(), false,
                List.of(primary, fallback), 4,
                new PdfExtractionEngineSelector.MathCorrectionPolicy(2, 2, 2,
                        java.time.Duration.ofMinutes(1)));

        ParsedFile result = selector.extract(new PdfExtractionRequest(
                new byte[] {1}, "application/pdf", "미래엔_고등수학.pdf",
                PdfExtractionOptions.defaults().withOcrMode("FORCE").withOcrLanguage("kor+eng")));

        assertThat(requests).containsExactly("primary:1-2", "primary:3-4", "fallback:3-4");
        assertThat(result.metadata())
                .containsEntry("koreanTextOcrProvider", "paddleocr+pymupdf4llm-korean-ocr")
                .containsEntry("koreanTextOcrPages", List.of(1, 2, 3, 4))
                .containsEntry("koreanTextOcrMissingPages", List.of())
                .containsEntry("koreanTextOcrComplete", true);
        assertThat(result.blocks().stream()
                .filter(block -> Boolean.TRUE.equals(block.metadata().get("textCorrectionOnly")))
                .map(block -> block.metadata().get("textCorrectionProvider")))
                .contains("paddleocr", "pymupdf4llm-korean-ocr");
    }

    @Test
    void hybridMathRouteSendsOnlyLowQualityMathPagesToVisionCorrection() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                12, 5, 0.0d, 1.0d, 0.0d, true, 0.0d, 1.0d, 1.0d, PdfDocumentKind.MATH_LIKE);
        PdfExtractionEngine baseline = new PdfExtractionEngine() {
            @Override
            public PdfExtractionEngineType type() {
                return PdfExtractionEngineType.PYMUPDF4LLM;
            }

            @Override
            public boolean supports(PdfExtractionRequest request) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request) {
                return new ParsedFile(DocumentFormat.PDF, "$xㅡ2+1$\n$x^2+1$\n$xㅡ3+1$\n$xㅡ4+1$", List.of(
                        studio.one.platform.textract.domain.model.ParsedBlock.text(
                                "page[2]/block[0]", studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                                "$xㅡ2+1$", 2, 0, Map.of("sourceRef", "page[2]/block[0]")),
                        studio.one.platform.textract.domain.model.ParsedBlock.text(
                                "page[7]/block[0]", studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                                "$xㅡ3+1$", 7, 1, Map.of("sourceRef", "page[7]/block[0]")),
                        studio.one.platform.textract.domain.model.ParsedBlock.text(
                                "page[9]/block[0]", studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                                "$xㅡ4+1$", 9, 2, Map.of("sourceRef", "page[9]/block[0]"))),
                        Map.of(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm"),
                        List.of(), List.of(), List.of(), List.of(), true);
            }
        };
        List<Integer> receivedPages = new ArrayList<>();
        MathVisionCorrectionClient visionClient = new MathVisionCorrectionClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String provider() {
                return "gemini";
            }

            @Override
            public ParsedFile correct(PdfExtractionRequest request, PdfDocumentAnalysis analysis, List<Integer> pages) {
                receivedPages.addAll(pages);
                return ParsedFile.textOnly(DocumentFormat.PDF, "$x^{2}+1$", request.filename());
            }
        };
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis, List.of(mathEngine("$x^2$", "pix2text")),
                true, 4, List.of(visionClient), true, pdfBox("fallback"), baseline);

        ParsedFile result = selector.extract(request(PdfExtractionOptions.defaults()
                .withOcrMode("FORCE")
                .withMathVisionCorrection(true)));

        assertThat(receivedPages).containsExactly(2, 7);
        assertThat(result.metadata())
                .containsEntry("mathVisionCorrectionPageCount", 2)
                .containsEntry("mathCorrectionPages", List.of(2, 7))
                .containsEntry("mathCorrectionPageSelection", "LOW_QUALITY_MATH_LIMITED");
    }

    @Test
    void hybridMathRoutePrioritizesMalformedPrimeAndJoinedVariablePages() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                44, 5, 0.0d, 1.0d, 0.0d, true, 0.0d, 1.0d, 1.0d, PdfDocumentKind.MATH_LIKE);
        PdfExtractionEngine baseline = new PdfExtractionEngine() {
            @Override
            public PdfExtractionEngineType type() {
                return PdfExtractionEngineType.PYMUPDF4LLM;
            }

            @Override
            public boolean supports(PdfExtractionRequest request) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request) {
                List<studio.one.platform.textract.domain.model.ParsedBlock> blocks = new ArrayList<>(List.of(
                        studio.one.platform.textract.domain.model.ParsedBlock.text(
                                "page[2]/block[0]", studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                                "$xㅡ2+1$", 2, 0, Map.of("sourceRef", "page[2]/block[0]")),
                        studio.one.platform.textract.domain.model.ParsedBlock.text(
                                "page[7]/block[0]", studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                                "$xㅡ3+1$", 7, 1, Map.of("sourceRef", "page[7]/block[0]")),
                        studio.one.platform.textract.domain.model.ParsedBlock.text(
                                "page[8]/block[0]", studio.one.platform.textract.domain.model.BlockType.TABLE,
                                "문제 006\n|A+B=52x' +2y-2y|\n|A-B=x’+3xry-6y|", 8, 2,
                                Map.of("sourceRef", "page[8]/block[0]"))));
                for (int page = 10; page <= 20; page++) {
                    blocks.add(studio.one.platform.textract.domain.model.ParsedBlock.text(
                            "page[" + page + "]/block[0]",
                            studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                            "$x????????????+1$", page, page,
                            Map.of("sourceRef", "page[" + page + "]/block[0]")));
                }
                return new ParsedFile(DocumentFormat.PDF, "math", blocks,
                        Map.of(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm"),
                        List.of(), List.of(), List.of(), List.of(), true);
            }
        };
        List<Integer> receivedPages = new ArrayList<>();
        MathVisionCorrectionClient visionClient = new MathVisionCorrectionClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String provider() {
                return "gemini";
            }

            @Override
            public ParsedFile correct(PdfExtractionRequest request, PdfDocumentAnalysis analysis, List<Integer> pages) {
                receivedPages.addAll(pages);
                return ParsedFile.textOnly(DocumentFormat.PDF, "$x^{2}+1$", request.filename());
            }
        };
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis, List.of(mathEngine("$x^2$", "pix2text")),
                true, 4, List.of(visionClient), true, pdfBox("fallback"), baseline);

        selector.extract(request(PdfExtractionOptions.defaults()
                .withOcrMode("FORCE")
                .withMathVisionCorrection(true)));

        assertThat(receivedPages).hasSize(2).first().isEqualTo(8);
    }

    @Test
    void hybridMathRouteSkipsVisionCorrectionWhenRequestDoesNotOptIn() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                12, 5, 0.0d, 1.0d, 0.0d, true, 0.0d, 1.0d, 1.0d, PdfDocumentKind.MATH_LIKE);
        PdfExtractionEngine baseline = pyMuPdf("baseline all pages");
        MathDocumentExtractionEngine mathEngine = mathEngine("$x^{2}+1$", "pix2text");
        MathVisionCorrectionClient visionClient = new MathVisionCorrectionClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String provider() {
                return "gemini";
            }

            @Override
            public ParsedFile correct(PdfExtractionRequest request, PdfDocumentAnalysis analysis, List<Integer> pages) {
                throw new AssertionError("Vision correction must not run without request opt-in");
            }
        };
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis, List.of(mathEngine), true, 2,
                List.of(visionClient), true, pdfBox("pdfbox text"), baseline);

        ParsedFile result = selector.extract(request(PdfExtractionOptions.defaults().withOcrMode("FORCE")));

        assertThat(result.blocks()).extracting(studio.one.platform.textract.domain.model.ParsedBlock::text)
                .doesNotContain("$a^{2}-b^{2}$");
        assertThat(result.metadata())
                .containsEntry("mathVisionCorrectionRequested", false)
                .containsEntry("mathVisionCorrectionApplied", false)
                .containsEntry("mathVisionFormulaBlockCount", 0)
                .containsEntry("mathVisionCorrectionPageCount", 0)
                .containsEntry("mathVisionCorrectionBatchCount", 0);
    }

    @Test
    void hybridMathRouteRecordsVisionCorrectionFailureMessage() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                12, 5, 0.0d, 1.0d, 0.0d, true, 0.0d, 1.0d, 1.0d, PdfDocumentKind.MATH_LIKE);
        PdfExtractionEngine baseline = pyMuPdf("baseline all pages");
        MathDocumentExtractionEngine mathEngine = mathEngine("$x^{2}+1$", "pix2text");
        MathVisionCorrectionClient visionClient = new MathVisionCorrectionClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String provider() {
                return "gemini";
            }

            @Override
            public ParsedFile correct(PdfExtractionRequest request, PdfDocumentAnalysis analysis, List<Integer> pages)
                    throws FileParseException {
                throw new FileParseException("Gemini math vision correction returned HTTP 504 body=timeout");
            }
        };
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis, List.of(mathEngine), true, 2,
                List.of(visionClient), true, pdfBox("pdfbox text"), baseline);

        ParsedFile result = selector.extract(request(PdfExtractionOptions.defaults()
                .withOcrMode("FORCE")
                .withMathVisionCorrection(true)));

        assertThat(result.metadata())
                .containsEntry("mathVisionCorrectionRequested", true)
                .containsEntry("mathVisionCorrectionApplied", false)
                .containsEntry("mathVisionCorrectionProvider", "gemini")
                .containsEntry("mathVisionCorrectionSkipReason", "CLIENT_FAILED")
                .containsEntry("mathVisionCorrectionErrorMessage",
                        "Gemini math vision correction returned HTTP 504")
                .containsEntry("mathVisionFormulaBlockCount", 0)
                .containsEntry("mathVisionCorrectionPageCount", 2)
                .containsEntry("mathVisionCorrectionBatchCount", 1)
                .containsEntry("mathVisionCorrectionFailedBatchCount", 1);
    }

    @Test
    void hybridMathRouteSendsOnlyMathSupplementPagesToVisionCorrectionInSmallBatches() throws Exception {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                12, 5, 0.0d, 1.0d, 0.0d, true, 0.0d, 1.0d, 1.0d, PdfDocumentKind.MATH_LIKE);
        byte[] sourcePdf = pdfBytes(12);
        PdfExtractionEngine baseline = pyMuPdf("baseline all pages");
        MathDocumentExtractionEngine mathEngine = mathEngine("$x^{2}+1$", "pix2text");
        List<Integer> receivedPageCounts = new ArrayList<>();
        List<List<Integer>> receivedPageBatches = new ArrayList<>();
        MathVisionCorrectionClient visionClient = new MathVisionCorrectionClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String provider() {
                return "gemini";
            }

            @Override
            public ParsedFile correct(PdfExtractionRequest request, PdfDocumentAnalysis analysis, List<Integer> pages)
                    throws FileParseException {
                receivedPageBatches.add(List.copyOf(pages));
                try (PDDocument document = Loader.loadPDF(request.bytes())) {
                    receivedPageCounts.add(document.getNumberOfPages());
                } catch (IOException ex) {
                    throw new FileParseException("invalid subset", ex);
                }
                return new ParsedFile(
                        DocumentFormat.PDF,
                        "$a^{2}-b^{2}$",
                        List.of(studio.one.platform.textract.domain.model.ParsedBlock.text(
                                "math-vision/page[12]/formula[0]",
                                studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                                "$a^{2}-b^{2}$",
                                12,
                                0,
                                Map.of("sourceRef", "math-vision/page[12]/formula[0]"))),
                        Map.of("mathVisionProvider", "gemini"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        true,
                        "$a^{2}-b^{2}$",
                        "markdown",
                        List.of());
            }
        };
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis, List.of(mathEngine), true, 4,
                List.of(visionClient), true, pdfBox("pdfbox text"), baseline);

        ParsedFile result = selector.extract(new PdfExtractionRequest(sourcePdf, "application/pdf", "source.pdf",
                PdfExtractionOptions.defaults()
                        .withOcrMode("FORCE")
                        .withMathVisionCorrection(true)));

        assertThat(receivedPageBatches).containsExactly(List.of(1, 2));
        assertThat(receivedPageCounts).containsExactly(2);
        assertThat(result.metadata())
                .containsEntry("mathVisionCorrectionPageCount", 2)
                .containsEntry("mathVisionCorrectionBatchCount", 1)
                .containsEntry("mathVisionCorrectionFailedBatchCount", 0);
    }

    @Test
    void explicitPyMuPdfWithForceOcrSetsLegacyOcrRequiredWhenMathEngineIsUnavailable() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                44, 5, 0.0d, 1.0d, 0.0d, true, 0.0d, 1.0d, 0.0d, PdfDocumentKind.MIXED);
        AtomicReference<PdfExtractionRequest> captured = new AtomicReference<>();
        PdfExtractionEngine pyMuPdf = new PdfExtractionEngine() {
            @Override
            public PdfExtractionEngineType type() {
                return PdfExtractionEngineType.PYMUPDF4LLM;
            }

            @Override
            public boolean supports(PdfExtractionRequest request) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request) {
                captured.set(request);
                ParsedFile file = ParsedFile.textOnly(DocumentFormat.PDF, "ocr fallback text", request.filename());
                return new ParsedFile(
                        file.format(),
                        file.plainText(),
                        file.blocks(),
                        Map.of(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm"),
                        file.warnings(),
                        file.pages(),
                        file.tables(),
                        file.images(),
                        true);
            }
        };
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis, List.of(),
                pdfBox("pdfbox text"), pyMuPdf);
        PdfExtractionOptions options = new PdfExtractionOptions(
                PdfExtractionMode.PYMUPDF4LLM,
                true,
                true,
                true,
                false,
                false,
                false,
                null,
                3,
                1024)
                .withOcrMode("FORCE")
                .withOcrLanguage("kor+eng");

        ParsedFile result = selector.extract(request(options));

        assertThat(captured.get().options().ocrRequired()).isTrue();
        assertThat(captured.get().options().ocrMode()).isEqualTo("FORCE");
        assertThat(captured.get().options().ocrLanguage()).isEqualTo("kor+eng");
        assertThat(result.metadata())
                .containsEntry(PdfExtractionEngineSelector.KEY_RECOMMENDED_ROUTE, "MATH_DOCUMENT")
                .containsEntry(PdfExtractionEngineSelector.KEY_ACTUAL_ROUTE, "OCR")
                .containsEntry(PdfExtractionEngineSelector.KEY_ENGINE_SELECTION_REASON,
                        "EXPLICIT_PYMUPDF4LLM_WITH_OCR")
                .containsEntry(PdfExtractionEngineSelector.KEY_MATH_FALLBACK_APPLIED, true)
                .containsEntry(PdfExtractionEngineSelector.KEY_OCR_REQUESTED_BY, "CLIENT");
        assertThat(result.warnings()).extracting(warning -> warning.canonicalCode())
                .contains("MATH_DOCUMENT_ENGINE_DISABLED");
    }

    @Test
    void autoForceOcrSetsLegacyOcrRequiredForOcrFallback() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                5, 2, 0.01d, 1.0d, 0.0d, true, 0.0d, 0.0d, 0.0d, PdfDocumentKind.SCANNED);
        AtomicReference<PdfExtractionRequest> captured = new AtomicReference<>();
        PdfExtractionEngine pyMuPdf = new PdfExtractionEngine() {
            @Override
            public PdfExtractionEngineType type() {
                return PdfExtractionEngineType.PYMUPDF4LLM;
            }

            @Override
            public boolean supports(PdfExtractionRequest request) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request) {
                captured.set(request);
                ParsedFile file = ParsedFile.textOnly(DocumentFormat.PDF, "ocr text", request.filename());
                return new ParsedFile(
                        file.format(),
                        file.plainText(),
                        file.blocks(),
                        Map.of(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm"),
                        file.warnings(),
                        file.pages(),
                        file.tables(),
                        file.images(),
                        true);
            }
        };
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis, List.of(),
                pdfBox("pdfbox text"), pyMuPdf);

        ParsedFile result = selector.extract(request(PdfExtractionOptions.defaults().withOcrMode("FORCE")));

        assertThat(captured.get().options().ocrRequired()).isTrue();
        assertThat(result.metadata())
                .containsEntry(PdfExtractionEngineSelector.KEY_RECOMMENDED_ROUTE, "OCR")
                .containsEntry(PdfExtractionEngineSelector.KEY_ACTUAL_ROUTE, "OCR")
                .containsEntry(PdfExtractionEngineSelector.KEY_ENGINE_SELECTION_REASON, "OCR_FORCED_BY_CLIENT")
                .containsEntry(PdfExtractionEngineSelector.KEY_OCR_REQUESTED_BY, "CLIENT");
    }

    @Test
    void autoRecordsOcrRecommendationWithoutApplyingOcrUntilClientForcesIt() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                5, 2, 0.01d, 1.0d, 0.0d, true, 0.0d, 0.0d, 0.0d, PdfDocumentKind.SCANNED);
        AtomicReference<PdfExtractionRequest> captured = new AtomicReference<>();
        PdfExtractionEngine pyMuPdf = new PdfExtractionEngine() {
            @Override
            public PdfExtractionEngineType type() {
                return PdfExtractionEngineType.PYMUPDF4LLM;
            }

            @Override
            public boolean supports(PdfExtractionRequest request) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request) {
                captured.set(request);
                ParsedFile file = ParsedFile.textOnly(DocumentFormat.PDF, "ocr text", request.filename());
                return new ParsedFile(
                        file.format(),
                        file.plainText(),
                        file.blocks(),
                        Map.of(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm"),
                        file.warnings(),
                        file.pages(),
                        file.tables(),
                        file.images(),
                        false);
            }
        };
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis, List.of(), pdfBox("pdfbox text"), pyMuPdf);

        ParsedFile result = selector.extract(request(PdfExtractionOptions.defaults()));

        assertThat(captured.get().options().ocrRequired()).isFalse();
        assertThat(result.metadata())
                .containsEntry(PdfExtractionEngineSelector.KEY_RECOMMENDED_ROUTE, "OCR")
                .containsEntry(PdfExtractionEngineSelector.KEY_ACTUAL_ROUTE, "PYMUPDF4LLM")
                .containsEntry(PdfExtractionEngineSelector.KEY_ENGINE_SELECTION_REASON,
                        "OCR_REQUIRES_CLIENT_FORCE")
                .containsEntry(PdfExtractionEngineSelector.KEY_OCR_REQUESTED_BY, "ANALYZER_RECOMMENDED");
    }

    @Test
    void disabledOcrModeSuppressesAnalyzerOcrRecommendation() {
        PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                5, 2, 0.01d, 1.0d, 0.0d, true, 0.0d, 0.0d, 0.0d, PdfDocumentKind.SCANNED);
        AtomicReference<PdfExtractionRequest> captured = new AtomicReference<>();
        PdfExtractionEngine pyMuPdf = new PdfExtractionEngine() {
            @Override
            public PdfExtractionEngineType type() {
                return PdfExtractionEngineType.PYMUPDF4LLM;
            }

            @Override
            public boolean supports(PdfExtractionRequest request) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request) {
                captured.set(request);
                ParsedFile file = ParsedFile.textOnly(DocumentFormat.PDF, "text", request.filename());
                return new ParsedFile(
                        file.format(),
                        file.plainText(),
                        file.blocks(),
                        Map.of(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm"),
                        file.warnings(),
                        file.pages(),
                        file.tables(),
                        file.images(),
                        false);
            }
        };
        PdfExtractionEngineSelector selector = selectorWithAnalysis(analysis, List.of(), pdfBox("pdfbox text"), pyMuPdf);

        ParsedFile result = selector.extract(request(PdfExtractionOptions.defaults().withOcrMode("DISABLED")));

        assertThat(captured.get().options().ocrRequired()).isFalse();
        assertThat(captured.get().options().ocrMode()).isEqualTo("DISABLED");
        assertThat(result.metadata())
                .containsEntry(PdfExtractionEngineSelector.KEY_RECOMMENDED_ROUTE, "PYMUPDF4LLM")
                .containsEntry(PdfExtractionEngineSelector.KEY_ACTUAL_ROUTE, "PYMUPDF4LLM")
                .containsEntry(PdfExtractionEngineSelector.KEY_OCR_REQUESTED_BY, "CLIENT_DISABLED");
    }

    private PdfExtractionEngineSelector selector(PdfExtractionEngine... engines) {
        return new PdfExtractionEngineSelector(List.of(engines));
    }

    private PdfExtractionEngineSelector selectorWithAnalysis(
            PdfDocumentAnalysis analysis,
            List<MathDocumentExtractionEngine> mathEngines,
            PdfExtractionEngine... engines) {
        return selectorWithAnalysis(analysis, mathEngines, false, 8, engines);
    }

    private PdfExtractionEngineSelector selectorWithAnalysis(
            PdfDocumentAnalysis analysis,
            List<MathDocumentExtractionEngine> mathEngines,
            boolean mathHybridEnabled,
            int mathHybridSamplePages,
            PdfExtractionEngine... engines) {
        return new PdfExtractionEngineSelector(List.of(engines), new PdfDocumentAnalyzer() {
            @Override
            public PdfDocumentAnalysis analyze(PdfExtractionRequest request) {
                return analysis;
            }
        }, mathEngines, true, 0.65d, mathHybridEnabled, mathHybridSamplePages);
    }

    private PdfExtractionEngineSelector selectorWithAnalysis(
            PdfDocumentAnalysis analysis,
            List<MathDocumentExtractionEngine> mathEngines,
            boolean mathHybridEnabled,
            int mathHybridSamplePages,
            List<MathVisionCorrectionClient> visionClients,
            boolean visionEnabled,
            PdfExtractionEngine... engines) {
        return new PdfExtractionEngineSelector(List.of(engines), new PdfDocumentAnalyzer() {
            @Override
            public PdfDocumentAnalysis analyze(PdfExtractionRequest request) {
                return analysis;
            }
        }, mathEngines, true, 0.65d, mathHybridEnabled, mathHybridSamplePages, visionClients, visionEnabled);
    }

    private PdfExtractionRequest request(PdfExtractionOptions options) {
        return new PdfExtractionRequest(new byte[] {1}, "application/pdf", "sample.pdf", options);
    }

    private byte[] pdfBytes(int pages) throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private PdfExtractionEngine pdfBox(String text) {
        return fixed(PdfExtractionEngineType.PDFBOX, "pdfbox", text);
    }

    private PdfExtractionEngine pyMuPdf(String text) {
        return fixed(PdfExtractionEngineType.PYMUPDF4LLM, "pymupdf4llm", text);
    }

    private PdfExtractionEngine garbledPyMuPdf(int pages, String text) {
        return new PdfExtractionEngine() {
            @Override
            public PdfExtractionEngineType type() {
                return PdfExtractionEngineType.PYMUPDF4LLM;
            }

            @Override
            public boolean supports(PdfExtractionRequest request) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request) {
                List<studio.one.platform.textract.domain.model.ParsedBlock> blocks = new ArrayList<>();
                for (int page = 1; page <= pages; page++) {
                    blocks.add(studio.one.platform.textract.domain.model.ParsedBlock.text(
                            "page[" + page + "]/block[0]",
                            studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                            text, page, page - 1, Map.of("sourceRef", "page[" + page + "]/block[0]")));
                }
                return new ParsedFile(DocumentFormat.PDF, text, blocks,
                        Map.of(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm"),
                        List.of(), List.of(), List.of(), List.of(), false,
                        text, "markdown", List.of());
            }
        };
    }

    private PdfExtractionEngine fixed(PdfExtractionEngineType type, String engineName, String text) {
        return new PdfExtractionEngine() {
            @Override
            public PdfExtractionEngineType type() {
                return type;
            }

            @Override
            public boolean supports(PdfExtractionRequest request) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request) {
                ParsedFile file = ParsedFile.textOnly(DocumentFormat.PDF, text, "sample.pdf");
                return new ParsedFile(
                        file.format(),
                        file.plainText(),
                        file.blocks(),
                        Map.of(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, engineName),
                        file.warnings(),
                        file.pages(),
                        file.tables(),
                        file.images(),
                        file.ocrApplied());
            }
        };
    }

    private MathDocumentExtractionEngine mathEngine(String markdown, String provider) {
        return new MathDocumentExtractionEngine() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public boolean supports(PdfExtractionRequest request, PdfDocumentAnalysis analysis) {
                return analysis.mathLike();
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis analysis) {
                return new ParsedFile(
                        DocumentFormat.PDF,
                        markdown,
                        List.of(studio.one.platform.textract.domain.model.ParsedBlock.text(
                                provider + "/block[0]",
                                studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                                markdown,
                                1,
                                0,
                                Map.of("sourceRef", "page[1]/" + provider + "-block[0]", "page", 1))),
                        Map.of(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, provider, "mathOcrProvider", provider),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        true,
                        markdown,
                        "markdown",
                        List.of());
            }
        };
    }

    private KoreanTextOcrClient koreanOcrClient(String provider, KoreanOcrExtraction extraction) {
        return batchedKoreanOcrClient(provider, 2, extraction);
    }

    private KoreanTextOcrClient batchedKoreanOcrClient(
            String provider, int batchSize, KoreanOcrExtraction extraction) {
        return new KoreanTextOcrClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String provider() {
                return provider;
            }

            @Override
            public int maxPagesPerRequest() {
                return batchSize;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis analysis)
                    throws FileParseException {
                return extraction.extract(request);
            }
        };
    }

    private ParsedFile koreanCorrection(PdfExtractionRequest request, String text) {
        List<studio.one.platform.textract.domain.model.ParsedBlock> blocks = new ArrayList<>();
        for (int page = request.options().pageFrom(); page <= request.options().pageTo(); page++) {
            blocks.add(studio.one.platform.textract.domain.model.ParsedBlock.text(
                    "page[" + page + "]/block[0]",
                    studio.one.platform.textract.domain.model.BlockType.PARAGRAPH,
                    text, page, page - 1, Map.of("sourceRef", "page[" + page + "]/block[0]")));
        }
        return new ParsedFile(DocumentFormat.PDF, text, blocks,
                Map.of(), List.of(), List.of(), List.of(), List.of(), true);
    }

    @FunctionalInterface
    private interface KoreanOcrExtraction {
        ParsedFile extract(PdfExtractionRequest request) throws FileParseException;
    }

    private PdfExtractionEngine failingPyMuPdf() {
        return new PdfExtractionEngine() {
            @Override
            public PdfExtractionEngineType type() {
                return PdfExtractionEngineType.PYMUPDF4LLM;
            }

            @Override
            public boolean supports(PdfExtractionRequest request) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request) throws FileParseException {
                throw new FileParseException("worker failed");
            }
        };
    }
}
