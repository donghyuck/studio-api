package studio.one.platform.textract.infrastructure.extractor.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.BlockType;
import studio.one.platform.textract.domain.model.ParseWarning;
import studio.one.platform.textract.domain.model.ParsedBlock;
import studio.one.platform.textract.domain.model.ParsedFile;

public class PdfExtractionEngineSelector {

    private static final Pattern MALFORMED_PRIME_EXPONENT = Pattern.compile(
            "(?i).*(?<![a-z])(?:[xyzabc][’'](?:\\d+)?|\\d{2,}[xyzabc][’'])(?![a-z]).*");
    private static final Pattern OCR_DIGIT_PRIME_EXPONENT = Pattern.compile(
            "(?i)\\d{2,}[xyzabc][’'](?![a-z])");
    private static final Pattern OCR_JOINED_MATH_VARIABLES = Pattern.compile(
            "(?i).*(?<![a-z])(?:[xyzabc][rv][xyzabc]|[xyzabc]{2,}r[xyzabc])(?![a-z]).*");

    public static final String KEY_EXTRACTION_ENGINE = "pdfExtractionEngine";
    public static final String KEY_FALLBACK_FROM = "pdfExtractionFallbackFrom";
    public static final String KEY_LARGE_PDF_EXTRACTION = "pdfLargeExtraction";
    public static final String KEY_LARGE_PDF_PARTS = "pdfExtractionParts";
    public static final String KEY_LARGE_PDF_TEXT_LENGTH = "pdfLargeExtractionTextLength";
    public static final String KEY_ANALYSIS = "pdfAnalysis";
    public static final String KEY_RECOMMENDED_ROUTE = "pdfRecommendedRoute";
    public static final String KEY_ACTUAL_ROUTE = "pdfActualRoute";
    public static final String KEY_ENGINE_SELECTION_REASON = "pdfEngineSelectionReason";
    public static final String KEY_MARKDOWN_QUALITY_STATUS = "markdownQualityStatus";
    public static final String KEY_MARKDOWN_QUALITY_ISSUES = "markdownQualityIssues";
    public static final String KEY_MATH_FALLBACK_APPLIED = "mathFallbackApplied";
    public static final String KEY_PAGE_PROVENANCE_STATUS = "pageProvenanceStatus";
    public static final String KEY_OCR_REQUESTED_BY = "ocrRequestedBy";
    public static final String KEY_OCR_DECISION_REASON = "ocrDecisionReason";
    public static final String KEY_PYMUPDF_STATUS = "pymupdfStatus";
    public static final String KEY_PYMUPDF_ERROR = "pymupdfError";
    public static final String KEY_FALLBACK_REASON = "pdfExtractionFallbackReason";

    private final List<PdfExtractionEngine> engines;
    private final PdfDocumentAnalyzer analyzer;
    private final List<MathDocumentExtractionEngine> mathEngines;
    private final boolean mathQualityGateEnabled;
    private final double mathQualityGateMinScore;
    private final boolean mathHybridEnabled;
    private final int mathHybridSamplePages;
    private final List<MathVisionCorrectionClient> mathVisionClients;
    private final boolean mathVisionCorrectionEnabled;
    private final List<KoreanTextOcrClient> koreanTextOcrClients;
    private final int koreanTextOcrMaxPages;
    private final MathCorrectionPolicy mathCorrectionPolicy;

    public PdfExtractionEngineSelector(List<PdfExtractionEngine> engines) {
        this(engines, new PdfDocumentAnalyzer(), List.of());
    }

    public PdfExtractionEngineSelector(
            List<PdfExtractionEngine> engines,
            PdfDocumentAnalyzer analyzer,
            List<MathDocumentExtractionEngine> mathEngines) {
        this(engines, analyzer, mathEngines, true, 0.65d);
    }

    public PdfExtractionEngineSelector(
            List<PdfExtractionEngine> engines,
            PdfDocumentAnalyzer analyzer,
            List<MathDocumentExtractionEngine> mathEngines,
            boolean mathQualityGateEnabled,
            double mathQualityGateMinScore) {
        this(engines, analyzer, mathEngines, mathQualityGateEnabled, mathQualityGateMinScore, true, 8);
    }

    public PdfExtractionEngineSelector(
            List<PdfExtractionEngine> engines,
            PdfDocumentAnalyzer analyzer,
            List<MathDocumentExtractionEngine> mathEngines,
            boolean mathQualityGateEnabled,
            double mathQualityGateMinScore,
            boolean mathHybridEnabled,
            int mathHybridSamplePages) {
        this(engines, analyzer, mathEngines, mathQualityGateEnabled, mathQualityGateMinScore,
                mathHybridEnabled, mathHybridSamplePages, List.of(), false);
    }

    public PdfExtractionEngineSelector(
            List<PdfExtractionEngine> engines,
            PdfDocumentAnalyzer analyzer,
            List<MathDocumentExtractionEngine> mathEngines,
            boolean mathQualityGateEnabled,
            double mathQualityGateMinScore,
            boolean mathHybridEnabled,
            int mathHybridSamplePages,
            List<MathVisionCorrectionClient> mathVisionClients,
            boolean mathVisionCorrectionEnabled) {
        this(engines, analyzer, mathEngines, mathQualityGateEnabled, mathQualityGateMinScore,
                mathHybridEnabled, mathHybridSamplePages, mathVisionClients, mathVisionCorrectionEnabled,
                List.of(), 0, MathCorrectionPolicy.compatibilityDefaults());
    }

    public PdfExtractionEngineSelector(
            List<PdfExtractionEngine> engines,
            PdfDocumentAnalyzer analyzer,
            List<MathDocumentExtractionEngine> mathEngines,
            boolean mathQualityGateEnabled,
            double mathQualityGateMinScore,
            boolean mathHybridEnabled,
            int mathHybridSamplePages,
            List<MathVisionCorrectionClient> mathVisionClients,
            boolean mathVisionCorrectionEnabled,
            List<KoreanTextOcrClient> koreanTextOcrClients,
            int koreanTextOcrMaxPages) {
        this(engines, analyzer, mathEngines, mathQualityGateEnabled, mathQualityGateMinScore,
                mathHybridEnabled, mathHybridSamplePages, mathVisionClients, mathVisionCorrectionEnabled,
                koreanTextOcrClients, koreanTextOcrMaxPages, MathCorrectionPolicy.compatibilityDefaults());
    }

    public PdfExtractionEngineSelector(
            List<PdfExtractionEngine> engines,
            PdfDocumentAnalyzer analyzer,
            List<MathDocumentExtractionEngine> mathEngines,
            boolean mathQualityGateEnabled,
            double mathQualityGateMinScore,
            boolean mathHybridEnabled,
            int mathHybridSamplePages,
            List<MathVisionCorrectionClient> mathVisionClients,
            boolean mathVisionCorrectionEnabled,
            List<KoreanTextOcrClient> koreanTextOcrClients,
            int koreanTextOcrMaxPages,
            MathCorrectionPolicy mathCorrectionPolicy) {
        this.engines = engines == null ? List.of() : List.copyOf(engines);
        this.analyzer = analyzer == null ? new PdfDocumentAnalyzer() : analyzer;
        this.mathEngines = mathEngines == null ? List.of() : List.copyOf(mathEngines);
        this.mathQualityGateEnabled = mathQualityGateEnabled;
        this.mathQualityGateMinScore = mathQualityGateMinScore <= 0.0d ? 0.65d : mathQualityGateMinScore;
        this.mathHybridEnabled = mathHybridEnabled;
        this.mathHybridSamplePages = Math.max(1, mathHybridSamplePages);
        this.mathVisionClients = mathVisionClients == null ? List.of() : List.copyOf(mathVisionClients);
        this.mathVisionCorrectionEnabled = mathVisionCorrectionEnabled;
        this.koreanTextOcrClients = koreanTextOcrClients == null ? List.of() : List.copyOf(koreanTextOcrClients);
        this.koreanTextOcrMaxPages = Math.max(0, koreanTextOcrMaxPages);
        this.mathCorrectionPolicy = mathCorrectionPolicy == null
                ? MathCorrectionPolicy.compatibilityDefaults()
                : mathCorrectionPolicy;
    }

    public ParsedFile extract(PdfExtractionRequest request) throws FileParseException {
        PdfExtractionOptions options = request.options();
        PdfDocumentAnalysis analysis = analyzer.analyze(request);
        return switch (options.engine()) {
            case PDFBOX -> withSelectionMetadata(
                    extractRequired(request, PdfExtractionEngineType.PDFBOX),
                    options,
                    analysis,
                    PdfExtractionRoute.PDFBOX,
                    PdfExtractionRoute.PDFBOX,
                    "EXPLICIT_PDFBOX",
                    List.of());
            case PYMUPDF4LLM -> extractExplicitPyMuPdf(request, analysis);
            case AUTO -> extractAuto(request, analysis);
        };
    }

    public boolean supports(PdfExtractionRequest request) {
        PdfExtractionOptions options = request.options();
        return switch (options.engine()) {
            case PDFBOX -> supports(request, PdfExtractionEngineType.PDFBOX);
            case PYMUPDF4LLM -> supports(request, PdfExtractionEngineType.PYMUPDF4LLM)
                    || (options.fallbackEnabled() && supports(request, PdfExtractionEngineType.PDFBOX));
            case AUTO -> supports(request, PdfExtractionEngineType.PDFBOX)
                    || supports(request, PdfExtractionEngineType.PYMUPDF4LLM);
        };
    }

    private ParsedFile extractExplicitPyMuPdf(PdfExtractionRequest request, PdfDocumentAnalysis analysis)
            throws FileParseException {
        PdfExtractionRoute recommendedRoute = analysis.mathLike()
                ? PdfExtractionRoute.MATH_DOCUMENT
                : request.options().ocrForceRequested() ? PdfExtractionRoute.OCR : PdfExtractionRoute.PYMUPDF4LLM;
        List<ParseWarning> selectionWarnings = List.of();
        if (analysis.mathLike() && shouldAttemptMathDocument(request)) {
            if (mathHybridEnabled) {
                ParsedFile hybridResult = extractHybridMathDocument(request, analysis);
                if (hybridResult != null) {
                    return withSelectionMetadata(hybridResult, hybridResultOptions(request), analysis,
                            PdfExtractionRoute.MATH_DOCUMENT,
                            PdfExtractionRoute.OCR,
                            request.options().ocrForceRequested()
                                    ? "EXPLICIT_PYMUPDF4LLM_WITH_OCR_MATH_HYBRID"
                                    : "EXPLICIT_PYMUPDF4LLM_MATH_HYBRID",
                            List.of());
                }
            }
            ParsedFile mathResult = extractMathDocument(request, analysis);
            if (mathResult != null) {
                return withSelectionMetadata(mathResult, request.options(), analysis,
                        PdfExtractionRoute.MATH_DOCUMENT,
                        PdfExtractionRoute.MATH_DOCUMENT,
                        request.options().ocrForceRequested()
                                ? "EXPLICIT_PYMUPDF4LLM_WITH_OCR_MATH_DOCUMENT"
                                : "EXPLICIT_PYMUPDF4LLM_MATH_DOCUMENT",
                        List.of());
            }
            selectionWarnings = List.of(mathDocumentFallbackWarning(
                    shouldApplyOcr(request) ? PdfExtractionRoute.OCR : PdfExtractionRoute.PYMUPDF4LLM,
                    "MATH_DOCUMENT_ENGINE_DISABLED",
                    true));
        }
        PdfExtractionRequest effectiveRequest = withLegacyOcrRequiredIfForced(request);
        PdfExtractionRoute actualRoute = request.options().ocrForceRequested()
                ? PdfExtractionRoute.OCR
                : PdfExtractionRoute.PYMUPDF4LLM;
        return withSelectionMetadata(
                extractPyMuPdfFirst(effectiveRequest),
                effectiveRequest.options(),
                analysis,
                recommendedRoute,
                actualRoute,
                request.options().ocrForceRequested() ? "EXPLICIT_PYMUPDF4LLM_WITH_OCR" : "EXPLICIT_PYMUPDF4LLM",
                selectionWarnings);
    }

    private ParsedFile extractAuto(PdfExtractionRequest request, PdfDocumentAnalysis analysis) throws FileParseException {
        PdfExtractionRoute recommendedRoute = recommendedRoute(request, analysis);
        if (recommendedRoute == PdfExtractionRoute.MATH_DOCUMENT && shouldAttemptMathDocument(request)) {
            if (mathHybridEnabled) {
                ParsedFile hybridResult = extractHybridMathDocument(request, analysis);
                if (hybridResult != null) {
                    return withSelectionMetadata(hybridResult, hybridResultOptions(request), analysis, recommendedRoute,
                            PdfExtractionRoute.OCR, "MATH_DOCUMENT_HYBRID_SELECTED", List.of());
                }
            }
            ParsedFile mathResult = extractMathDocument(request, analysis);
            if (mathResult != null) {
                return withSelectionMetadata(mathResult, request.options(), analysis, recommendedRoute,
                        PdfExtractionRoute.MATH_DOCUMENT, "MATH_DOCUMENT_ENGINE_SELECTED", List.of());
            }
        }
        PdfExtractionRequest effectiveRequest = request;
        PdfExtractionRoute actualRoute = recommendedRoute;
        String reason = "AUTO_DEFAULT";
        List<ParseWarning> selectionWarnings = List.of();
        if (recommendedRoute == PdfExtractionRoute.MATH_DOCUMENT) {
            actualRoute = shouldApplyOcr(request)
                    ? PdfExtractionRoute.OCR
                    : PdfExtractionRoute.PYMUPDF4LLM;
            reason = shouldAttemptMathDocument(request) ? "MATH_DOCUMENT_ENGINE_DISABLED"
                    : "MATH_DOCUMENT_REQUIRES_CLIENT_FORCE";
            selectionWarnings = List.of(mathDocumentFallbackWarning(
                    actualRoute,
                    reason,
                    shouldAttemptMathDocument(request)));
        }
        if (recommendedRoute == PdfExtractionRoute.OCR && !shouldApplyOcr(request)) {
            actualRoute = PdfExtractionRoute.PYMUPDF4LLM;
            reason = "OCR_REQUIRES_CLIENT_FORCE";
        }
        if (actualRoute == PdfExtractionRoute.OCR && !request.options().ocrRequired()) {
            effectiveRequest = withLegacyOcrRequiredIfForced(request);
            if (recommendedRoute != PdfExtractionRoute.MATH_DOCUMENT) {
                reason = request.options().ocrForceRequested()
                        ? "OCR_FORCED_BY_CLIENT"
                        : "OCR_RECOMMENDED_BY_ANALYZER";
            }
        }
        if ((actualRoute == PdfExtractionRoute.OCR || actualRoute == PdfExtractionRoute.PYMUPDF4LLM)
                && supports(effectiveRequest, PdfExtractionEngineType.PYMUPDF4LLM)) {
            return withSelectionMetadata(extractPyMuPdfFirst(effectiveRequest), effectiveRequest.options(), analysis, recommendedRoute,
                    actualRoute, reason, selectionWarnings);
        }
        if (request.options().prefersPyMuPdf4Llm()) {
            return withSelectionMetadata(extractPyMuPdfFirst(request), request.options(), analysis, recommendedRoute,
                    PdfExtractionRoute.PYMUPDF4LLM, "AUTO_PREFERS_PYMUPDF4LLM", selectionWarnings);
        }
        PdfExtractionEngine pdfBox = find(PdfExtractionEngineType.PDFBOX);
        if (pdfBox != null && pdfBox.supports(request)) {
            return withSelectionMetadata(pdfBox.extract(request), request.options(), analysis, recommendedRoute,
                    PdfExtractionRoute.PDFBOX, "AUTO_PDFBOX_AVAILABLE", selectionWarnings);
        }
        return withSelectionMetadata(extractRequired(request, PdfExtractionEngineType.PYMUPDF4LLM), request.options(), analysis,
                recommendedRoute, PdfExtractionRoute.PYMUPDF4LLM, "AUTO_PDFBOX_UNAVAILABLE", selectionWarnings);
    }

    private ParsedFile extractPyMuPdfFirst(PdfExtractionRequest request) throws FileParseException {
        PdfExtractionOptions options = request.options();
        PdfExtractionEngine pyMuPdf = find(PdfExtractionEngineType.PYMUPDF4LLM);
        if (pyMuPdf == null || !pyMuPdf.supports(request)) {
            if (options.fallbackEnabled()) {
                return withFallbackWarning(
                        extractRequired(request, PdfExtractionEngineType.PDFBOX),
                        "PYMUPDF4LLM_UNAVAILABLE",
                        null);
            }
            throw new FileParseException("PyMuPDF4LLM PDF extraction engine is not available.");
        }

        try {
            if (shouldExtractLargePdfInParts(request)) {
                ParsedFile extracted = extractLargePdfInParts(request, pyMuPdf);
                int failedParts = integer(extracted.metadata().get("failedPartCount")) == null
                        ? 0
                        : integer(extracted.metadata().get("failedPartCount"));
                int emptyParts = integer(extracted.metadata().get("emptyPartCount")) == null
                        ? 0
                        : integer(extracted.metadata().get("emptyPartCount"));
                String status = failedParts > 0 || emptyParts > 0 ? "PARTIAL" : "ACCEPTED";
                return withPyMuPdfStatus(extracted, status, null);
            }
            ParsedFile extracted = pyMuPdf.extract(request);
            if (textLength(extracted) <= 0) {
                throw new FileParseException("PyMuPDF4LLM returned no usable text.");
            }
            return withPyMuPdfStatus(extracted, "ACCEPTED", null);
        } catch (FileParseException ex) {
            if (!options.fallbackEnabled()) {
                throw ex;
            }
            return withFallbackWarning(
                    extractRequired(request, PdfExtractionEngineType.PDFBOX),
                    "PYMUPDF4LLM_FAILED",
                    ex);
        }
    }

    private ParsedFile extractRequired(PdfExtractionRequest request, PdfExtractionEngineType type)
            throws FileParseException {
        PdfExtractionEngine engine = find(type);
        if (engine == null || !engine.supports(request)) {
            throw new FileParseException(type.name().toLowerCase(Locale.ROOT)
                    + " PDF extraction engine is not available.");
        }
        return engine.extract(request);
    }

    private PdfExtractionEngine find(PdfExtractionEngineType type) {
        return engines.stream()
                .filter(engine -> engine.type() == type)
                .findFirst()
                .orElse(null);
    }

    private boolean supports(PdfExtractionRequest request, PdfExtractionEngineType type) {
        PdfExtractionEngine engine = find(type);
        return engine != null && engine.supports(request);
    }

    private boolean shouldExtractLargePdfInParts(PdfExtractionRequest request) {
        PdfExtractionOptions options = request.options();
        return options.largePdfEnabled()
                && !options.rangeRequested()
                && options.pageCount() != null
                && options.pageCount() >= options.largePdfPageThreshold();
    }

    private ParsedFile extractLargePdfInParts(PdfExtractionRequest request, PdfExtractionEngine pyMuPdf)
            throws FileParseException {
        PdfExtractionOptions options = request.options();
        int pageCount = options.pageCount();
        int batchSize = options.largePdfBatchSize();
        List<ParsedFile> successfulParts = new ArrayList<>();
        List<Map<String, Object>> partSummaries = new ArrayList<>();
        List<ParseWarning> warnings = new ArrayList<>();
        int failedParts = 0;
        int emptyParts = 0;
        int textLength = 0;

        for (int pageFrom = 1; pageFrom <= pageCount; pageFrom += batchSize) {
            int pageTo = Math.min(pageCount, pageFrom + batchSize - 1);
            long started = System.nanoTime();
            try {
                ParsedFile part = pyMuPdf.extract(new PdfExtractionRequest(
                        request.bytes(),
                        request.contentType(),
                        request.filename(),
                        options.forPageRange(pageFrom, pageTo)));
                int partTextLength = textLength(part);
                String partMarkdown = markdownText(part);
                long elapsedMs = elapsedMs(started);
                Map<String, Object> summary = partSummary(pageFrom, pageTo, "COMPLETED", "pymupdf4llm",
                        partTextLength, partMarkdown, null, null, elapsedMs, part.metadata());
                partSummaries.add(summary);
                PdfExtractionProgressContext.publishPart(summary);
                if (partTextLength == 0) {
                    emptyParts++;
                    warnings.add(ParseWarning.warning(
                            "PYMUPDF4LLM_EMPTY_TEXT",
                            "PyMuPDF4LLM returned empty text for PDF page range.",
                            "page[" + pageFrom + "-" + pageTo + "]",
                            Map.of("pageFrom", pageFrom, "pageTo", pageTo)));
                } else {
                    successfulParts.add(part);
                    textLength += partTextLength;
                }
            } catch (FileParseException ex) {
                failedParts++;
                long elapsedMs = elapsedMs(started);
                Map<String, Object> summary = partSummary(pageFrom, pageTo, "FAILED", "pymupdf4llm",
                        0, null, "PYMUPDF4LLM_FAILED", safeDiagnosticMessage(ex.getMessage()), elapsedMs, Map.of());
                partSummaries.add(summary);
                PdfExtractionProgressContext.publishPart(summary);
                warnings.add(ParseWarning.warning(
                        "PYMUPDF4LLM_PART_FAILED",
                        "PyMuPDF4LLM failed for PDF page range.",
                        "page[" + pageFrom + "-" + pageTo + "]",
                        Map.of("pageFrom", pageFrom, "pageTo", pageTo)));
                if (!options.continueOnPartFailure()
                        || (options.maxPartFailures() > 0 && failedParts > options.maxPartFailures())) {
                    throw ex;
                }
            }
        }

        Map<String, Object> metadata = new LinkedHashMap<>(fileMetadata(request));
        metadata.put(KEY_EXTRACTION_ENGINE, "pymupdf4llm");
        metadata.put("ocrRequired", options.ocrRequired());
        metadata.put("ocrMode", options.ocrMode());
        if (options.ocrLanguage() != null) {
            metadata.put("ocrLanguage", options.ocrLanguage());
        }
        metadata.put(KEY_LARGE_PDF_EXTRACTION, true);
        metadata.put(KEY_LARGE_PDF_PARTS, partSummaries);
        metadata.put(KEY_LARGE_PDF_TEXT_LENGTH, textLength);
        metadata.put("pageCount", pageCount);
        metadata.put("largePdfBatchSize", batchSize);
        metadata.put("failedPartCount", failedParts);
        metadata.put("emptyPartCount", emptyParts);

        List<ParsedBlock> pages = new ArrayList<>();
        List<ParsedBlock> blocks = new ArrayList<>();
        List<studio.one.platform.textract.domain.model.ExtractedTable> tables = new ArrayList<>();
        List<studio.one.platform.textract.domain.model.ExtractedImage> images = new ArrayList<>();
        StringBuilder markdown = new StringBuilder();
        for (ParsedFile part : successfulParts) {
            pages.addAll(part.pages());
            blocks.addAll(part.blocks());
            tables.addAll(part.tables());
            images.addAll(part.images());
            warnings.addAll(part.warnings());
            Integer pageFrom = integer(part.metadata().get("pageFrom"));
            Integer pageTo = integer(part.metadata().get("pageTo"));
            if (markdown.length() > 0) {
                markdown.append("\n\n");
            }
            if (pageFrom != null && pageTo != null) {
                markdown.append("<!-- page_range: ").append(pageFrom).append("-").append(pageTo).append(" -->\n\n");
            }
            markdown.append(markdownText(part));
        }

        if (textLength == 0) {
            warnings.add(ParseWarning.warning(
                    "NO_EXTRACTABLE_CONTENT",
                    "No extractable text was produced by any PDF page range.",
                    "document",
                    Map.of("pageCount", pageCount, "partCount", partSummaries.size())));
        }

        return new ParsedFile(
                studio.one.platform.textract.domain.model.DocumentFormat.PDF,
                markdown.toString(),
                blocks,
                metadata,
                warnings,
                pages,
                tables,
                images,
                false,
                markdown.toString(),
                "markdown",
                List.of());
    }

    private PdfExtractionRoute recommendedRoute(PdfExtractionRequest request, PdfDocumentAnalysis analysis) {
        if (analysis.mathLike()) {
            return PdfExtractionRoute.MATH_DOCUMENT;
        }
        if (shouldRecommendOcr(request, analysis)) {
            return PdfExtractionRoute.OCR;
        }
        if (supports(request, PdfExtractionEngineType.PYMUPDF4LLM)) {
            return PdfExtractionRoute.PYMUPDF4LLM;
        }
        return PdfExtractionRoute.PDFBOX;
    }

    private boolean shouldAttemptMathDocument(PdfExtractionRequest request) {
        return request.options().ocrForceRequested() && !request.options().ocrDisabled();
    }

    private boolean shouldRecommendOcr(PdfExtractionRequest request, PdfDocumentAnalysis analysis) {
        if (request.options().ocrDisabled()) {
            return false;
        }
        return request.options().ocrForceRequested() || analysis.ocrRecommended();
    }

    private boolean shouldApplyOcr(PdfExtractionRequest request) {
        return request.options().ocrForceRequested() && !request.options().ocrDisabled();
    }

    private PdfExtractionRequest withLegacyOcrRequiredIfForced(PdfExtractionRequest request) {
        if (!request.options().ocrForceRequested() || request.options().ocrRequired()) {
            return request;
        }
        return new PdfExtractionRequest(request.bytes(), request.contentType(), request.filename(),
                request.options().withOcrRequired(true));
    }

    private ParseWarning mathDocumentFallbackWarning(
            PdfExtractionRoute actualRoute,
            String reason,
            boolean attemptedMath) {
        Map<String, Object> warningMetadata = new LinkedHashMap<>();
        warningMetadata.put("actualRoute", actualRoute.name());
        warningMetadata.put("mathFallbackApplied", attemptedMath);
        return ParseWarning.warning(
                reason,
                attemptedMath
                        ? "Math document extraction was recommended but no enabled MathDocumentExtractionEngine handled the PDF."
                        : "Math document extraction was recommended but was not requested by the client.",
                "document",
                warningMetadata);
    }

    private ParsedFile extractMathDocument(PdfExtractionRequest request, PdfDocumentAnalysis analysis)
            throws FileParseException {
        FileParseException lastFailure = null;
        List<ParseWarning> qualityWarnings = new ArrayList<>();
        for (MathDocumentExtractionEngine engine : mathEngines) {
            if (engine.enabled() && engine.supports(request, analysis)) {
                try {
                    ParsedFile extracted = engine.extract(request, analysis);
                    MarkdownQualityAssessment quality = assessMathMarkdown(extracted);
                    if (!mathQualityGateEnabled || quality.accepted()) {
                        return withQualityMetadata(extracted, quality, !qualityWarnings.isEmpty(), qualityWarnings);
                    }
                    qualityWarnings.add(ParseWarning.warning(
                            "MATH_MARKDOWN_QUALITY_GATE_FAILED",
                            "Math document extraction result did not pass the markdown quality gate.",
                            "document",
                            Map.of(
                                    "issues", quality.issues(),
                                    "score", quality.score(),
                                    "provider", String.valueOf(extracted.metadata().getOrDefault("mathOcrProvider",
                                            extracted.metadata().getOrDefault(KEY_EXTRACTION_ENGINE, "unknown"))))));
                } catch (FileParseException ex) {
                    lastFailure = ex;
                }
            }
        }
        if (lastFailure != null && mathEngines.size() == 1) {
            throw lastFailure;
        }
        return null;
    }

    private ParsedFile extractHybridMathDocument(PdfExtractionRequest request, PdfDocumentAnalysis analysis)
            throws FileParseException {
        if (mathEngines.isEmpty()) {
            return null;
        }

        PdfExtractionRequest baselineRequest = new PdfExtractionRequest(
                request.bytes(),
                request.contentType(),
                request.filename(),
                request.options().withOcrRequired(false).withOcrMode("AUTO"));
        ParsedFile baseline = extractPyMuPdfFirst(baselineRequest);
        MathPageSelection pageSelection = mathCorrectionPages(request, analysis, baseline);
        List<ParsedFile> mathParts = extractMathSupplementParts(request, analysis, pageSelection.pages());

        List<ParseWarning> warnings = new ArrayList<>(baseline.warnings());
        List<ParsedBlock> blocks = new ArrayList<>(baseline.blocks());
        TextCorrectionResult textCorrection = extractKoreanTextCorrections(
                request, analysis, baseline, nextOrder(blocks));
        blocks.addAll(textCorrection.blocks());
        warnings.addAll(textCorrection.warnings());
        List<Map<String, Object>> partSummaries = new ArrayList<>();
        Set<Integer> pageContentReplacementPages = new LinkedHashSet<>();
        List<ParsedBlock> mathPageBlocks = List.of();
        List<ParsedBlock> mathFormulaBlocks = mathFormulaSupplementBlocks(mathParts,
                nextOrder(blocks) + mathPageBlocks.size(), pageContentReplacementPages);
        VisionCorrectionResult visionCorrection = extractVisionCorrection(request, analysis, pageSelection.pages(),
                nextOrder(blocks) + mathPageBlocks.size() + mathFormulaBlocks.size());

        for (ParsedFile part : mathParts) {
            Map<String, Object> summary = mathSupplementSummary(part);
            partSummaries.add(summary);
            warnings.addAll(part.warnings());
        }
        warnings.addAll(visionCorrection.warnings());
        blocks.addAll(mathPageBlocks);
        blocks.addAll(mathFormulaBlocks);
        blocks.addAll(visionCorrection.blocks());

        Map<String, Object> metadata = new LinkedHashMap<>(baseline.metadata());
        boolean hybridApplied = !mathParts.isEmpty() || textCorrection.applied() || visionCorrection.applied();
        putHybridMetadata(metadata, partSummaries, hybridApplied, pageSelection);
        metadata.put("mathHybridPageContentReplacementCount", pageContentReplacementPages.size());
        metadata.put("mathHybridPageContentReplacementPages", List.copyOf(pageContentReplacementPages));
        metadata.put("mathHybridFormulaBlockCount", mathFormulaBlocks.size());
        metadata.put("mathVisionCorrectionRequested", request.options().mathVisionCorrection());
        metadata.put("mathVisionCorrectionApplied", visionCorrection.applied());
        if (visionCorrection.provider() != null && !visionCorrection.provider().isBlank()) {
            metadata.put("mathVisionCorrectionProvider", visionCorrection.provider());
        }
        if (visionCorrection.skipReason() != null && !visionCorrection.skipReason().isBlank()) {
            metadata.put("mathVisionCorrectionSkipReason", visionCorrection.skipReason());
        }
        if (visionCorrection.errorMessage() != null && !visionCorrection.errorMessage().isBlank()) {
            metadata.put("mathVisionCorrectionErrorMessage", visionCorrection.errorMessage());
        }
        metadata.put("mathVisionFormulaBlockCount", visionCorrection.blocks().size());
        metadata.put("mathVisionCorrectionPageCount", visionCorrection.pages().size());
        metadata.put("mathVisionCorrectionPages", visionCorrection.pages());
        metadata.put("mathVisionCorrectionBatchCount", visionCorrection.batchCount());
        metadata.put("mathVisionCorrectionFailedBatchCount", visionCorrection.failedBatchCount());
        metadata.put("koreanTextOcrApplied", textCorrection.applied());
        metadata.put("koreanTextOcrPages", textCorrection.pages());
        metadata.put("koreanTextOcrRequestedPages", textCorrection.requestedPages());
        metadata.put("koreanTextOcrMissingPages", textCorrection.requestedPages().stream()
                .filter(page -> !textCorrection.pages().contains(page))
                .toList());
        metadata.put("koreanTextOcrComplete", textCorrection.requestedPages().isEmpty()
                || textCorrection.pages().containsAll(textCorrection.requestedPages()));
        metadata.put("koreanTextOcrBlockCount", textCorrection.blocks().size());
        if (textCorrection.provider() != null) {
            metadata.put("koreanTextOcrProvider", textCorrection.provider());
        }
        return new ParsedFile(
                baseline.format(),
                baseline.plainText(),
                blocks,
                metadata,
                warnings,
                baseline.pages(),
                baseline.tables(),
                baseline.images(),
                baseline.ocrApplied(),
                baseline.markdown(),
                baseline.contentFormat(),
                baseline.locators());
    }

    private VisionCorrectionResult extractVisionCorrection(PdfExtractionRequest request, PdfDocumentAnalysis analysis,
            List<Integer> pages, int startOrder) {
        if (!request.options().mathVisionCorrection()) {
            return VisionCorrectionResult.empty();
        }
        if (!mathVisionCorrectionEnabled) {
            return VisionCorrectionResult.skipped("SERVER_DISABLED");
        }
        if (mathVisionClients.isEmpty()) {
            return VisionCorrectionResult.skipped("NO_CLIENT_CONFIGURED");
        }
        if (pages.isEmpty()) {
            return VisionCorrectionResult.skipped("NO_MATH_CORRECTION_PAGES");
        }
        boolean hasConfiguredClient = false;
        for (MathVisionCorrectionClient client : mathVisionClients) {
            if (client == null || !client.available()) {
                continue;
            }
            hasConfiguredClient = true;
            List<ParsedBlock> blocks = new ArrayList<>();
            List<ParseWarning> warnings = new ArrayList<>();
            String firstErrorMessage = null;
            int failedBatchCount = 0;
            int attemptedBatchCount = 0;
            List<List<Integer>> batches = pageBatches(pages, mathCorrectionPolicy.waveSize());
            long deadlineNanos = correctionDeadlineNanos();
            for (List<Integer> batch : batches) {
                if (System.nanoTime() >= deadlineNanos) {
                    warnings.add(ParseWarning.warning("MATH_VISION_TIME_BUDGET_EXHAUSTED",
                            "Math vision correction stopped after reaching its time budget.", "document",
                            Map.of("timeBudgetMs", mathCorrectionPolicy.timeBudget().toMillis())));
                    break;
                }
                attemptedBatchCount++;
                try {
                    ParsedFile corrected = client.correct(visionCorrectionRequest(request, batch), analysis, batch);
                    blocks.addAll(mathVisionBlocks(corrected, startOrder + blocks.size()));
                    if (corrected != null) {
                        warnings.addAll(corrected.warnings());
                    }
                } catch (FileParseException ex) {
                    failedBatchCount++;
                    String errorMessage = safeDiagnosticMessage(ex.getMessage());
                    if (firstErrorMessage == null || firstErrorMessage.isBlank()) {
                        firstErrorMessage = errorMessage;
                    }
                    warnings.add(ParseWarning.warning(
                            "MATH_VISION_CORRECTION_FAILED",
                            "Math vision correction failed.",
                            "document",
                            Map.of("provider", client.provider(), "pages", batch, "errorMessage", errorMessage)));
                }
            }
            if (!blocks.isEmpty()) {
                return new VisionCorrectionResult(
                        true,
                        client.provider(),
                        null,
                        firstErrorMessage,
                        blocks,
                        warnings,
                        pages,
                        attemptedBatchCount,
                        failedBatchCount);
            }
            if (failedBatchCount > 0) {
                return new VisionCorrectionResult(
                        false,
                        client.provider(),
                        "CLIENT_FAILED",
                        firstErrorMessage,
                        List.of(),
                        warnings,
                        pages,
                        attemptedBatchCount,
                        failedBatchCount);
            }
        }
        return VisionCorrectionResult.skipped(hasConfiguredClient ? "NO_RESULT" : "NO_AVAILABLE_CLIENT");
    }

    private List<Integer> lowQualityMathPages(ParsedFile baseline) {
        if (baseline == null || baseline.blocks().isEmpty()) {
            return List.of();
        }
        Map<Integer, Integer> scores = new LinkedHashMap<>();
        Set<Integer> malformedEquationPages = new LinkedHashSet<>();
        for (ParsedBlock block : baseline.blocks()) {
            if (block == null || block.page() == null || block.text() == null) {
                continue;
            }
            String text = block.text().strip();
            boolean severeMalformedEquation = severeMalformedEquationText(text);
            if (text.isBlank() || (!mathLikeText(text) && !severeMalformedEquation)) {
                continue;
            }
            if (lowQualityMathText(text) || severeMalformedEquation) {
                scores.merge(block.page(), lowQualityMathScore(text), Integer::sum);
                if (severeMalformedEquation) {
                    malformedEquationPages.add(block.page());
                }
            }
        }
        List<Integer> ranked = scores.entrySet().stream()
                .sorted((left, right) -> {
                    int byScore = Integer.compare(right.getValue(), left.getValue());
                    return byScore != 0 ? byScore : Integer.compare(left.getKey(), right.getKey());
                })
                .map(Map.Entry::getKey)
                .toList();
        if (malformedEquationPages.isEmpty()) {
            return ranked;
        }
        LinkedHashSet<Integer> prioritized = new LinkedHashSet<>();
        malformedEquationPages.stream()
                .sorted()
                .limit(Math.min(2, malformedEquationPages.size()))
                .forEach(prioritized::add);
        prioritized.addAll(ranked);
        return List.copyOf(prioritized);
    }

    private boolean severeMalformedEquationText(String text) {
        String value = text == null ? "" : text;
        return OCR_DIGIT_PRIME_EXPONENT.matcher(value).find();
    }

    private int lowQualityMathScore(String text) {
        String value = text == null ? "" : text;
        int score = 1;
        score += (int) value.chars().filter(ch -> ch == '\u3161' || ch == '?').count();
        score += value.contains("@$") ? 2 : 0;
        score += value.contains("\"$") || value.contains("$\"") ? 2 : 0;
        score += value.chars().filter(ch -> ch == '$').count() % 2 == 0 ? 0 : 3;
        score += MALFORMED_PRIME_EXPONENT.matcher(value).matches() ? 10 : 0;
        score += OCR_DIGIT_PRIME_EXPONENT.matcher(value).find() ? 50 : 0;
        score += OCR_JOINED_MATH_VARIABLES.matcher(value).matches() ? 8 : 0;
        return score;
    }

    private TextCorrectionResult extractKoreanTextCorrections(PdfExtractionRequest request,
            PdfDocumentAnalysis analysis, ParsedFile baseline, int startOrder) throws FileParseException {
        boolean catastrophicBaseline = catastrophicKoreanBaseline(request, analysis, baseline);
        if (koreanTextOcrMaxPages <= 0 || koreanTextOcrClients.isEmpty()) {
            if (catastrophicBaseline) {
                throw new FileParseException(
                        "Korean text OCR is required because the baseline text is unusable, but no provider is configured.");
            }
            return TextCorrectionResult.empty();
        }
        List<Integer> pages = (catastrophicBaseline
                ? requestedPages(request.options(), analysis.pageCount())
                : lowQualityKoreanPages(baseline)).stream()
                .limit(koreanTextOcrMaxPages)
                .toList();
        if (pages.isEmpty()) {
            return TextCorrectionResult.empty();
        }
        List<ParsedBlock> corrections = new ArrayList<>();
        List<ParseWarning> warnings = new ArrayList<>();
        List<Integer> completedPages = new ArrayList<>();
        List<String> appliedProviders = new ArrayList<>();
        for (KoreanTextOcrClient client : koreanTextOcrClients) {
            if (client == null || !client.available()) {
                continue;
            }
            List<Integer> remainingPages = pages.stream()
                    .filter(page -> !completedPages.contains(page))
                    .toList();
            if (remainingPages.isEmpty()) {
                break;
            }
            int correctionCountBeforeProvider = corrections.size();
            for (List<Integer> batch : contiguousPageBatches(remainingPages, client.maxPagesPerRequest())) {
                int pageFrom = batch.get(0);
                int pageTo = batch.get(batch.size() - 1);
                PdfExtractionOptions pageOptions = request.options().forPageRange(pageFrom, pageTo)
                        .withOcrRequired(true)
                        .withOcrMode("FORCE");
                PdfExtractionRequest pageRequest = new PdfExtractionRequest(request.bytes(), request.contentType(),
                        request.filename(), pageOptions);
                try {
                    ParsedFile corrected = client.extract(pageRequest, analysis);
                    for (ParsedBlock block : corrected.blocks()) {
                        if (block == null || block.text() == null || block.text().isBlank()
                                || !containsHangul(block.text())) {
                            continue;
                        }
                        Integer correctedPage = block.page();
                        if (correctedPage == null && batch.size() == 1) {
                            correctedPage = pageFrom;
                        }
                        if (correctedPage == null || !batch.contains(correctedPage)) {
                            continue;
                        }
                        String sourceRef = "korean-ocr/page[" + correctedPage + "]/block["
                                + corrections.size() + "]";
                        Map<String, Object> metadata = new LinkedHashMap<>(block.metadata());
                        metadata.put("sourceRef", sourceRef);
                        metadata.put("textCorrectionOnly", true);
                        metadata.put("textPageContentReplacement", true);
                        metadata.put("textCorrectionProvider", client.provider());
                        metadata.put("contentRole", "BODY");
                        metadata.put("confidence", 0.7d);
                        metadata.put("mergeRole", "TEXT");
                        metadata.put("mergePriority", 300);
                        metadata.put("catastrophicKoreanBaseline", catastrophicBaseline);
                        corrections.add(ParsedBlock.text(sourceRef, block.blockType(), block.text(), correctedPage,
                                startOrder + corrections.size(), metadata));
                        if (!completedPages.contains(correctedPage)) {
                            completedPages.add(correctedPage);
                        }
                    }
                    warnings.addAll(corrected.warnings());
                } catch (FileParseException ex) {
                    warnings.add(ParseWarning.warning("KOREAN_TEXT_OCR_FAILED",
                            "Korean text OCR correction failed.", "page[" + pageFrom + "]",
                            Map.of("provider", client.provider(), "pages", batch,
                                    "errorMessage", safeDiagnosticMessage(String.valueOf(ex.getMessage())))));
                    if (catastrophicBaseline) {
                        break;
                    }
                }
            }
            if (corrections.size() > correctionCountBeforeProvider) {
                appliedProviders.add(client.provider());
            }
        }
        List<Integer> missingPages = pages.stream()
                .filter(page -> !completedPages.contains(page))
                .toList();
        if (catastrophicBaseline && !missingPages.isEmpty()) {
            throw new FileParseException(
                    "Korean text OCR failed to return usable text for all required pages of a baseline with catastrophic Korean text damage.");
        }
        if (!corrections.isEmpty()) {
            return new TextCorrectionResult(true, String.join("+", appliedProviders), corrections, completedPages,
                    pages, warnings);
        }
        return TextCorrectionResult.empty();
    }

    private boolean catastrophicKoreanBaseline(PdfExtractionRequest request,
            PdfDocumentAnalysis analysis, ParsedFile baseline) {
        if (request == null || analysis == null || baseline == null
                || !analysis.ocrRecommended() || !koreanOcrRequested(request)) {
            return false;
        }
        String text = markdownText(baseline);
        long letters = text.codePoints().filter(Character::isLetter).count();
        if (letters < 100) {
            return false;
        }
        long hangul = text.codePoints().filter(this::isHangulSyllable).count();
        long latin = text.codePoints().filter(this::isAsciiLatinLetter).count();
        double hangulRatio = (double) hangul / letters;
        double latinRatio = (double) latin / letters;
        return hangulRatio < 0.02d && latinRatio >= 0.50d;
    }

    private boolean koreanOcrRequested(PdfExtractionRequest request) {
        String language = request.options().ocrLanguage();
        return request.options().ocrForceRequested()
                && ((language != null && language.toLowerCase(Locale.ROOT).contains("kor"))
                        || containsKoreanScript(request.filename()));
    }

    private List<Integer> requestedPages(PdfExtractionOptions options, int analyzedPageCount) {
        int from = options.pageFrom() == null ? 1 : options.pageFrom();
        int to = options.pageTo() == null ? analyzedPageCount : Math.min(options.pageTo(), analyzedPageCount);
        if (options.maxPages() != null) {
            to = Math.min(to, from + options.maxPages() - 1);
        }
        to = Math.min(to, from + koreanTextOcrMaxPages - 1);
        if (to < from) {
            return List.of();
        }
        List<Integer> pages = new ArrayList<>(to - from + 1);
        for (int page = from; page <= to; page++) {
            pages.add(page);
        }
        return pages;
    }

    private List<Integer> lowQualityKoreanPages(ParsedFile baseline) {
        if (baseline == null || baseline.blocks().isEmpty()) {
            return List.of();
        }
        Map<Integer, Integer> scores = new LinkedHashMap<>();
        for (ParsedBlock block : baseline.blocks()) {
            if (block == null || block.page() == null || block.text() == null || block.text().isBlank()) {
                continue;
            }
            int score = koreanGarbledScore(block.text());
            if (score > 0) {
                scores.merge(block.page(), score, Integer::sum);
            }
        }
        return scores.entrySet().stream()
                .sorted((left, right) -> {
                    int byScore = Integer.compare(right.getValue(), left.getValue());
                    return byScore != 0 ? byScore : Integer.compare(left.getKey(), right.getKey());
                })
                .map(Map.Entry::getKey)
                .toList();
    }

    private int koreanGarbledScore(String text) {
        int score = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if ((ch >= 0x3131 && ch <= 0x318E) || ch == '\uFFFD') {
                score++;
            }
        }
        return score;
    }

    private boolean containsHangul(String text) {
        return text != null && text.codePoints().anyMatch(this::isHangulSyllable);
    }

    private boolean containsKoreanScript(String text) {
        return text != null && text.codePoints().anyMatch(ch -> isHangulSyllable(ch)
                || (ch >= 0x1100 && ch <= 0x11FF)
                || (ch >= 0x3131 && ch <= 0x318E));
    }

    private boolean isHangulSyllable(int ch) {
        return ch >= 0xAC00 && ch <= 0xD7A3;
    }

    private boolean isAsciiLatinLetter(int ch) {
        return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
    }

    private boolean mathLikeText(String text) {
        return text.contains("$")
                || text.matches(".*[0-9A-Za-z)][=+\\-*/^][0-9A-Za-z({].*")
                || text.matches(".*\\\\(frac|sqrt|overline|mathrm|mathbf|mathbb|times|div|pm|leq|geq|neq)\\b.*");
    }

    private boolean lowQualityMathText(String text) {
        String value = text == null ? "" : text.strip();
        long dollars = value.chars().filter(ch -> ch == '$').count();
        return value.indexOf('\u3161') >= 0
                || value.contains("@$")
                || value.contains("\"$")
                || value.contains("$\"")
                || dollars % 2 != 0
                || value.matches(".*(?:\\bOO\\b|\\bSS\\b|\\bSAS\\b|[?°]).*")
                || MALFORMED_PRIME_EXPONENT.matcher(value).matches()
                || OCR_JOINED_MATH_VARIABLES.matcher(value).matches();
    }

    private List<List<Integer>> pageBatches(List<Integer> pages, int batchSize) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        int size = Math.max(1, batchSize);
        List<List<Integer>> batches = new ArrayList<>();
        for (int index = 0; index < pages.size(); index += size) {
            batches.add(List.copyOf(pages.subList(index, Math.min(index + size, pages.size()))));
        }
        return batches;
    }

    private List<List<Integer>> contiguousPageBatches(List<Integer> pages, int batchSize) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        int size = Math.max(1, batchSize);
        List<List<Integer>> batches = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        for (Integer page : pages) {
            if (page == null) {
                continue;
            }
            boolean contiguous = current.isEmpty() || page == current.get(current.size() - 1) + 1;
            if (!contiguous || current.size() >= size) {
                batches.add(List.copyOf(current));
                current.clear();
            }
            current.add(page);
        }
        if (!current.isEmpty()) {
            batches.add(List.copyOf(current));
        }
        return batches;
    }

    private PdfExtractionRequest visionCorrectionRequest(PdfExtractionRequest request, List<Integer> pages)
            throws FileParseException {
        if (pages == null || pages.isEmpty() || !looksLikePdf(request.bytes())) {
            return request;
        }
        try (PDDocument source = Loader.loadPDF(request.bytes());
                PDDocument target = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (Integer page : pages) {
                if (page == null || page < 1 || page > source.getNumberOfPages()) {
                    continue;
                }
                target.importPage(source.getPage(page - 1));
            }
            if (target.getNumberOfPages() == 0 || target.getNumberOfPages() == source.getNumberOfPages()) {
                return request;
            }
            target.save(output);
            PdfExtractionOptions options = request.options()
                    .withPageCount(target.getNumberOfPages())
                    .forPageRange(1, target.getNumberOfPages());
            String filename = request.filename() == null || request.filename().isBlank()
                    ? "math-vision-pages.pdf"
                    : request.filename().replaceFirst("(?i)\\.pdf$", "") + "-math-vision-pages.pdf";
            return new PdfExtractionRequest(output.toByteArray(), request.contentType(), filename, options);
        } catch (IOException ex) {
            throw new FileParseException("Failed to build math vision page subset PDF: " + ex.getMessage(), ex);
        }
    }

    private boolean looksLikePdf(byte[] bytes) {
        return bytes != null
                && bytes.length >= 4
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F';
    }

    private List<ParsedBlock> mathVisionBlocks(ParsedFile corrected, int startOrder) {
        if (corrected == null || corrected.blocks().isEmpty()) {
            return List.of();
        }
        List<ParsedBlock> blocks = new ArrayList<>();
        int order = startOrder;
        for (ParsedBlock block : corrected.blocks()) {
            String text = normalizeMathCandidate(block.text());
            if (!acceptedMathCandidate(text)) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>(block.metadata());
            metadata.put("mathVisionCorrectionOnly", true);
            metadata.put("mathSupplementSource", "VISION_LLM");
            metadata.put("mergeRole", "MATH");
            metadata.put("mergePriority", 250);
            metadata.putIfAbsent("mathVisionProvider", corrected.metadata().getOrDefault("mathVisionProvider", "unknown"));
            metadata.putIfAbsent("confidence", 0.6d);
            String sourceRef = block.sourceRef().isBlank()
                    ? "math-vision/page[" + (block.page() == null ? "unknown" : block.page()) + "]/formula[" + blocks.size() + "]"
                    : block.sourceRef();
            metadata.put("sourceRef", sourceRef);
            blocks.add(ParsedBlock.text(sourceRef, BlockType.PARAGRAPH, text, block.page(), order++, metadata));
        }
        return blocks;
    }

    private record VisionCorrectionResult(
            boolean applied,
            String provider,
            String skipReason,
            String errorMessage,
            List<ParsedBlock> blocks,
            List<ParseWarning> warnings,
            List<Integer> pages,
            int batchCount,
            int failedBatchCount) {
        static VisionCorrectionResult empty() {
            return new VisionCorrectionResult(false, null, null, null, List.of(), List.of(), List.of(), 0, 0);
        }

        static VisionCorrectionResult skipped(String reason) {
            return new VisionCorrectionResult(false, null, reason, null, List.of(), List.of(), List.of(), 0, 0);
        }
    }

    private int nextOrder(List<ParsedBlock> blocks) {
        int max = -1;
        for (ParsedBlock block : blocks) {
            Integer order = block.order();
            if (order != null && order > max) {
                max = order;
            }
        }
        return max + 1;
    }

    private List<ParsedBlock> mathPageReplacementBlocks(List<ParsedFile> parts, int startOrder,
            Set<Integer> replacementPages) {
        List<ParsedBlock> blocks = new ArrayList<>();
        int order = startOrder;
        for (ParsedFile part : parts) {
            Integer page = integer(part.metadata().get("pageFrom"));
            List<ParsedBlock> pageBlocks = part.blocks().stream()
                    .filter(block -> block != null && block.text() != null && !block.text().isBlank())
                    .toList();
            if (page == null || !sufficientPageContent(pageBlocks)) {
                continue;
            }
            replacementPages.add(page);
            String provider = String.valueOf(part.metadata().getOrDefault("mathOcrProvider",
                    part.metadata().getOrDefault(KEY_EXTRACTION_ENGINE, "unknown")));
            for (ParsedBlock source : pageBlocks) {
                String path = "math-page/page[" + page + "]/block[" + blocks.size() + "]";
                Map<String, Object> metadata = new LinkedHashMap<>(source.metadata());
                metadata.put("sourceRef", path);
                metadata.put("mathPageContentReplacement", true);
                metadata.put("mathOcrProvider", provider);
                metadata.put("mathSupplementSource", "HYBRID_MATH_OCR");
                metadata.put("confidence", 0.55d);
                blocks.add(ParsedBlock.text(path, source.blockType(), source.text(), page, order++, metadata));
            }
        }
        return blocks;
    }

    private boolean sufficientPageContent(List<ParsedBlock> blocks) {
        int proseBlocks = 0;
        int proseLength = 0;
        for (ParsedBlock block : blocks) {
            String text = block.text() == null ? "" : block.text().strip();
            if (text.isBlank() || acceptedMathCandidate(text)) {
                continue;
            }
            proseBlocks++;
            proseLength += text.length();
        }
        return proseBlocks >= 2 && proseLength >= 80;
    }

    private List<ParsedBlock> mathFormulaSupplementBlocks(List<ParsedFile> parts, int startOrder,
            Set<Integer> replacedPages) {
        List<ParsedBlock> blocks = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        int order = startOrder;
        for (ParsedFile part : parts) {
            Integer page = integer(part.metadata().get("pageFrom"));
            if (page != null && replacedPages.contains(page)) {
                continue;
            }
            String provider = String.valueOf(part.metadata().getOrDefault("mathOcrProvider",
                    part.metadata().getOrDefault(KEY_EXTRACTION_ENGINE, "unknown")));
            for (String candidate : mathFormulaCandidates(markdownText(part))) {
                if (!seen.add(candidate)) {
                    continue;
                }
                String path = "math-supplement/page[" + (page == null ? "unknown" : page)
                        + "]/formula[" + blocks.size() + "]";
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("sourceRef", path);
                metadata.put("mathSupplementOnly", true);
                metadata.put("mathOcrProvider", provider);
                putIfPresent(metadata, "pageFrom", part.metadata().get("pageFrom"));
                putIfPresent(metadata, "pageTo", part.metadata().get("pageTo"));
                metadata.put("mathSupplementSource", "HYBRID_MATH_OCR");
                metadata.put("confidence", 0.5d);
                metadata.put("mergeRole", "MATH");
                metadata.put("mergePriority", 300);
                blocks.add(ParsedBlock.text(path, BlockType.PARAGRAPH, candidate, page, order++, metadata));
            }
        }
        return blocks;
    }

    private List<String> mathFormulaCandidates(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        StringBuilder displayMath = null;
        for (String rawLine : markdown.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.strip();
            if (line.equals("$$")) {
                if (displayMath == null) {
                    displayMath = new StringBuilder();
                } else {
                    String candidate = displayMath.toString().strip();
                    if (acceptedMathCandidate(candidate)) {
                        candidates.add("$$\n" + candidate + "\n$$");
                    }
                    displayMath = null;
                }
                continue;
            }
            if (displayMath != null) {
                if (!line.isBlank()) {
                    displayMath.append(line).append('\n');
                }
                continue;
            }
            if (acceptedMathCandidate(line)) {
                candidates.add(normalizeMathCandidate(line));
            }
        }
        return candidates;
    }

    private boolean acceptedMathCandidate(String value) {
        String raw = value == null ? "" : value.strip();
        if (raw.matches("(?i)^(OO+|Oo|oo|O00|00|SS|SAS|BS|BA|SHS|NOS|Sis!?|eT|loin|Bal|xm|me)$")) {
            return false;
        }
        String text = normalizeMathCandidate(value);
        if (text.isBlank() || text.length() > 220) {
            return false;
        }
        if (text.startsWith("![") || text.matches(".*[\\u4E00-\\u9FFF].*")) {
            return false;
        }
        if (balancedInlineMath(text)) {
            return true;
        }
        if (text.matches(".*\\\\(frac|sqrt|overline|underline|mathrm|mathbf|mathbb|cdot|circ|times|div|pm|leq|geq|neq)\\b.*")) {
            return true;
        }
        if (text.matches("^[A-Za-z0-9{}()\\[\\]\\\\^_+\\-*/=.,:;\\s]+$")
                && text.matches(".*[=+\\-*/^].*")
                && text.matches(".*[0-9xyabclmn].*")
                && text.length() <= 120) {
            return true;
        }
        return false;
    }

    private String normalizeMathCandidate(String value) {
        if (value == null) {
            return "";
        }
        String text = value.strip()
                .replace('−', '-')
                .replace('ㅡ', '-')
                .replaceAll("^[-*+]\\s+", "")
                .replaceAll("\\s+", " ");
        if (text.startsWith("$") || text.startsWith("\\[") || text.startsWith("$$")) {
            return text;
        }
        if (text.matches(".*\\\\(frac|sqrt|overline|underline|mathrm|mathbf|mathbb|cdot|circ|times|div|pm|leq|geq|neq)\\b.*")
                || text.matches("^[A-Za-z0-9{}()\\[\\]\\\\^_+\\-*/=.,:;\\s]+$")) {
            return "$" + text + "$";
        }
        return text;
    }

    private boolean balancedInlineMath(String text) {
        long dollars = text.chars().filter(ch -> ch == '$').count();
        return dollars >= 2 && dollars % 2 == 0 && !text.contains("$\\$");
    }

    private List<ParsedFile> extractMathSupplementParts(PdfExtractionRequest request, PdfDocumentAnalysis analysis,
            List<Integer> pages) {
        if (pages.isEmpty()) {
            return List.of();
        }
        List<ParsedFile> parts = new ArrayList<>();
        Set<MathDocumentExtractionEngine> unavailableEngines = new HashSet<>();
        long deadlineNanos = correctionDeadlineNanos();
        for (Integer page : pages) {
            if (System.nanoTime() >= deadlineNanos) {
                break;
            }
            PdfExtractionRequest partRequest = new PdfExtractionRequest(
                    request.bytes(),
                    request.contentType(),
                    request.filename(),
                    request.options().forPageRange(page, page));
            for (MathDocumentExtractionEngine engine : mathEngines) {
                if (unavailableEngines.contains(engine) || !engine.enabled() || !engine.supports(partRequest, analysis)) {
                    continue;
                }
                try {
                    ParsedFile extracted = engine.extract(partRequest, analysis);
                    if (textLength(extracted) > 0) {
                        parts.add(extracted);
                        break;
                    }
                } catch (FileParseException ignored) {
                    // A failed supplement engine is unlikely to recover for the next page. Keep trying
                    // later providers for this page, but do not repeat its timeout for every page.
                    unavailableEngines.add(engine);
                }
            }
        }
        return parts;
    }

    private MathPageSelection mathCorrectionPages(PdfExtractionRequest request, PdfDocumentAnalysis analysis,
            ParsedFile baseline) {
        List<Integer> lowQualityPages = lowQualityMathPages(baseline);
        if (!lowQualityPages.isEmpty()) {
            int count = Math.min(mathCorrectionPolicy.maxPages(), lowQualityPages.size());
            String reason = lowQualityPages.size() > count
                    ? "LOW_QUALITY_MATH_LIMITED"
                    : "LOW_QUALITY_MATH";
            return new MathPageSelection(List.copyOf(lowQualityPages.subList(0, count)), reason);
        }
        List<Integer> sampled = mathSupplementPages(request, analysis);
        int count = Math.min(mathCorrectionPolicy.fallbackPages(), sampled.size());
        return new MathPageSelection(List.copyOf(sampled.subList(0, count)), "FALLBACK_SAMPLE");
    }

    private List<Integer> mathSupplementPages(PdfExtractionRequest request, PdfDocumentAnalysis analysis) {
        int pageCount = request.options().pageCount() != null && request.options().pageCount() > 0
                ? request.options().pageCount()
                : analysis == null ? 0 : analysis.pageCount();
        if (pageCount <= 0) {
            return List.of();
        }
        int sampleCount = Math.min(pageCount, mathHybridSamplePages);
        LinkedHashSet<Integer> pages = new LinkedHashSet<>();
        if (sampleCount <= 0) {
            return List.of();
        }
        if (pageCount <= sampleCount) {
            for (int page = 1; page <= pageCount; page++) {
                pages.add(page);
            }
            return List.copyOf(pages);
        }
        int leading = Math.min(3, sampleCount);
        for (int page = 1; page <= leading; page++) {
            pages.add(page);
        }
        int remaining = sampleCount - pages.size();
        for (int i = 1; i <= remaining; i++) {
            int page = 1 + (int) Math.round((pageCount - 1) * (i / (double) remaining));
            pages.add(Math.max(1, Math.min(pageCount, page)));
        }
        for (int page = 1; pages.size() < sampleCount && page <= pageCount; page++) {
            pages.add(page);
        }
        return List.copyOf(pages);
    }

    private void putHybridMetadata(Map<String, Object> metadata, List<Map<String, Object>> partSummaries,
            boolean applied, MathPageSelection pageSelection) {
        metadata.put("mathHybridApplied", applied);
        metadata.put("mathHybridBaselineRoute", "PYMUPDF4LLM");
        metadata.put("mathHybridSamplePages", mathHybridSamplePages);
        metadata.put("mathHybridSupplementParts", partSummaries);
        metadata.put("mathHybridSupplementPageCount", partSummaries.size());
        metadata.put("mathCorrectionPages", pageSelection.pages());
        metadata.put("mathCorrectionPageSelection", pageSelection.reason());
        metadata.put("mathCorrectionMaxPages", mathCorrectionPolicy.maxPages());
        metadata.put("mathCorrectionWaveSize", mathCorrectionPolicy.waveSize());
        metadata.put("mathCorrectionTimeBudgetMs", mathCorrectionPolicy.timeBudget().toMillis());
        metadata.put(KEY_MARKDOWN_QUALITY_STATUS, "REVIEW_REQUIRED");
        metadata.put("markdownQualityStatus", "REVIEW_REQUIRED");
        metadata.put(KEY_MARKDOWN_QUALITY_ISSUES,
                List.of("MATH_DOCUMENT_HYBRID_REVIEW_REQUIRED", "MATH_OCR_SUPPLEMENT_LIMITED"));
        metadata.put("markdownQualityIssues",
                List.of("MATH_DOCUMENT_HYBRID_REVIEW_REQUIRED", "MATH_OCR_SUPPLEMENT_LIMITED"));
    }

    private record MathPageSelection(List<Integer> pages, String reason) {
        private MathPageSelection {
            pages = pages == null ? List.of() : List.copyOf(pages);
            reason = reason == null || reason.isBlank() ? "NONE" : reason;
        }
    }

    private long correctionDeadlineNanos() {
        long budgetNanos = mathCorrectionPolicy.timeBudget().toNanos();
        long now = System.nanoTime();
        return Long.MAX_VALUE - now < budgetNanos ? Long.MAX_VALUE : now + budgetNanos;
    }

    public record MathCorrectionPolicy(int maxPages, int fallbackPages, int waveSize, Duration timeBudget) {
        public MathCorrectionPolicy {
            maxPages = Math.max(1, maxPages);
            fallbackPages = Math.max(1, Math.min(maxPages, fallbackPages));
            waveSize = Math.max(1, Math.min(maxPages, waveSize));
            timeBudget = timeBudget == null || timeBudget.isNegative() || timeBudget.isZero()
                    ? Duration.ofMinutes(5)
                    : timeBudget;
        }

        public static MathCorrectionPolicy compatibilityDefaults() {
            return new MathCorrectionPolicy(2, 2, 2, Duration.ofMinutes(5));
        }
    }

    private record TextCorrectionResult(
            boolean applied,
            String provider,
            List<ParsedBlock> blocks,
            List<Integer> pages,
            List<Integer> requestedPages,
            List<ParseWarning> warnings) {
        private TextCorrectionResult {
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
            pages = pages == null ? List.of() : List.copyOf(pages);
            requestedPages = requestedPages == null ? List.of() : List.copyOf(requestedPages);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        private static TextCorrectionResult empty() {
            return new TextCorrectionResult(false, null, List.of(), List.of(), List.of(), List.of());
        }
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private Map<String, Object> mathSupplementSummary(ParsedFile part) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pageFrom", part.metadata().get("pageFrom"));
        summary.put("pageTo", part.metadata().get("pageTo"));
        summary.put("provider", part.metadata().getOrDefault("mathOcrProvider",
                part.metadata().getOrDefault(KEY_EXTRACTION_ENGINE, "unknown")));
        summary.put("textLength", textLength(part));
        summary.put("blockCount", part.blocks().size());
        summary.put("elapsedMs", part.metadata().get("elapsedMs"));
        return summary;
    }

    private PdfExtractionOptions hybridResultOptions(PdfExtractionRequest request) {
        return withLegacyOcrRequiredIfForced(request).options();
    }

    private ParsedFile withSelectionMetadata(
            ParsedFile file,
            PdfExtractionOptions options,
            PdfDocumentAnalysis analysis,
            PdfExtractionRoute recommendedRoute,
            PdfExtractionRoute actualRoute,
            String reason,
            List<ParseWarning> extraWarnings) {
        PdfExtractionRoute effectiveActualRoute = effectiveActualRoute(file, actualRoute);
        Map<String, Object> metadata = new LinkedHashMap<>(file.metadata());
        metadata.put(KEY_ANALYSIS, analysis.metadata());
        metadata.put(KEY_RECOMMENDED_ROUTE, recommendedRoute.name());
        metadata.put(KEY_ACTUAL_ROUTE, effectiveActualRoute.name());
        metadata.put(KEY_ENGINE_SELECTION_REASON, reason);
        metadata.put("recommendedRoute", recommendedRoute.name());
        metadata.put("actualRoute", effectiveActualRoute.name());
        metadata.put("engineSelectionReason", reason);
        metadata.put("ocrRequired", options.ocrRequired());
        metadata.put("ocrMode", options.ocrMode());
        if (options.ocrLanguage() != null) {
            metadata.put("ocrLanguage", options.ocrLanguage());
        }
        putOcrDecisionMetadata(metadata, options, analysis, effectiveActualRoute, reason);
        if (recommendedRoute == PdfExtractionRoute.MATH_DOCUMENT && effectiveActualRoute != PdfExtractionRoute.MATH_DOCUMENT) {
            boolean attemptedMath = options.ocrForceRequested() && !options.ocrDisabled();
            metadata.put(KEY_MATH_FALLBACK_APPLIED, attemptedMath);
            metadata.put("mathFallbackApplied", attemptedMath);
            if (attemptedMath) {
                metadata.putIfAbsent(KEY_MARKDOWN_QUALITY_STATUS, "REVIEW_REQUIRED");
                metadata.putIfAbsent("markdownQualityStatus", "REVIEW_REQUIRED");
                metadata.putIfAbsent(KEY_MARKDOWN_QUALITY_ISSUES, List.of("MATH_DOCUMENT_FALLBACK_USED"));
                metadata.putIfAbsent("markdownQualityIssues", List.of("MATH_DOCUMENT_FALLBACK_USED"));
            }
        }
        metadata.putIfAbsent("extractionEngine", String.valueOf(metadata.getOrDefault(KEY_EXTRACTION_ENGINE,
                effectiveActualRoute.name().toLowerCase(Locale.ROOT))));
        List<ParseWarning> warnings = new ArrayList<>();
        if (extraWarnings != null) {
            warnings.addAll(extraWarnings);
        }
        warnings.addAll(file.warnings());
        return copy(file, metadata, warnings);
    }

    private ParsedFile withQualityMetadata(
            ParsedFile file,
            MarkdownQualityAssessment quality,
            boolean fallbackApplied,
            List<ParseWarning> priorWarnings) {
        Map<String, Object> metadata = new LinkedHashMap<>(file.metadata());
        metadata.put(KEY_MARKDOWN_QUALITY_STATUS, quality.status());
        metadata.put(KEY_MARKDOWN_QUALITY_ISSUES, quality.issues());
        metadata.put(KEY_MATH_FALLBACK_APPLIED, fallbackApplied);
        metadata.put(KEY_PAGE_PROVENANCE_STATUS, quality.pageProvenanceStatus());
        metadata.put("markdownQualityScore", quality.score());
        metadata.put("markdownQualityStatus", quality.status());
        metadata.put("markdownQualityIssues", quality.issues());
        metadata.put("mathFallbackApplied", fallbackApplied);
        metadata.put("pageProvenanceStatus", quality.pageProvenanceStatus());
        List<ParseWarning> warnings = new ArrayList<>();
        if (priorWarnings != null) {
            warnings.addAll(priorWarnings);
        }
        warnings.addAll(file.warnings());
        return copy(file, metadata, warnings);
    }

    private PdfExtractionRoute effectiveActualRoute(ParsedFile file, PdfExtractionRoute requestedActualRoute) {
        if (requestedActualRoute == PdfExtractionRoute.MATH_DOCUMENT) {
            return PdfExtractionRoute.MATH_DOCUMENT;
        }
        if (requestedActualRoute == PdfExtractionRoute.OCR && file.ocrApplied()) {
            return PdfExtractionRoute.OCR;
        }
        Object engine = file.metadata().get(KEY_EXTRACTION_ENGINE);
        if (engine != null) {
            String value = String.valueOf(engine).toLowerCase(Locale.ROOT);
            if (value.contains("pymupdf")) {
                return requestedActualRoute == PdfExtractionRoute.OCR && file.ocrApplied()
                        ? PdfExtractionRoute.OCR
                        : PdfExtractionRoute.PYMUPDF4LLM;
            }
            if (value.contains("pdfbox")) {
                return requestedActualRoute == PdfExtractionRoute.OCR && file.ocrApplied()
                        ? PdfExtractionRoute.OCR
                        : PdfExtractionRoute.PDFBOX;
            }
        }
        return requestedActualRoute;
    }

    private ParsedFile copy(ParsedFile file, Map<String, Object> metadata, List<ParseWarning> warnings) {
        return new ParsedFile(
                file.format(),
                file.plainText(),
                file.blocks(),
                metadata,
                warnings,
                file.pages(),
                file.tables(),
                file.images(),
                file.ocrApplied(),
                file.markdown(),
                file.contentFormat(),
                file.locators());
    }

    private void putOcrDecisionMetadata(Map<String, Object> metadata, PdfExtractionOptions options,
            PdfDocumentAnalysis analysis, PdfExtractionRoute actualRoute, String reason) {
        String requestedBy = "NONE";
        if (options.ocrDisabled()) {
            requestedBy = "CLIENT_DISABLED";
        } else if (options.ocrForceRequested()) {
            requestedBy = "CLIENT";
        } else if (analysis.ocrRecommended()) {
            requestedBy = "ANALYZER_RECOMMENDED";
        }
        metadata.put(KEY_OCR_REQUESTED_BY, requestedBy);
        metadata.put("ocrRequestedBy", requestedBy);
        metadata.put(KEY_OCR_DECISION_REASON, reason);
        metadata.put("ocrDecisionReason", reason);
    }

    private Map<String, Object> fileMetadata(PdfExtractionRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (request.contentType() != null) {
            metadata.put("contentType", request.contentType());
        }
        if (request.filename() != null) {
            metadata.put("filename", request.filename());
        }
        return metadata;
    }

    private Map<String, Object> partSummary(int pageFrom, int pageTo, String status, String engine,
            int textLength, String markdownText, String errorCode, String errorMessage, long elapsedMs,
            Map<String, Object> metadata) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pageFrom", pageFrom);
        summary.put("pageTo", pageTo);
        summary.put("status", status);
        summary.put("engine", engine);
        summary.put("textLength", textLength);
        summary.put("markdownText", markdownText);
        summary.put("errorCode", errorCode);
        summary.put("errorMessage", errorMessage);
        summary.put("elapsedMs", elapsedMs);
        summary.put("metadata", metadata == null ? Map.of() : metadata);
        return summary;
    }

    private int textLength(ParsedFile file) {
        String text = markdownText(file);
        return text == null ? 0 : text.strip().length();
    }

    private String markdownText(ParsedFile file) {
        if (file == null) {
            return "";
        }
        return file.markdown() == null || file.markdown().isBlank() ? file.plainText() : file.markdown();
    }

    private MarkdownQualityAssessment assessMathMarkdown(ParsedFile file) {
        List<String> issues = new ArrayList<>();
        String markdown = markdownText(file);
        if (markdown.isBlank()) {
            issues.add("MARKDOWN_BLANK");
        }
        List<String> lines = markdown.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
        if (!lines.isEmpty()) {
            double shortRatio = lines.stream().filter(line -> line.length() <= 5).count() / (double) lines.size();
            double latinNoiseRatio = lines.stream().filter(this::looksLikeLatinNoise).count() / (double) lines.size();
            if (shortRatio >= 0.18d) {
                issues.add("FRAGMENTED_SHORT_LINES");
            }
            if (latinNoiseRatio >= 0.05d) {
                issues.add("OCR_LATIN_NOISE");
            }
        }
        long dollars = markdown.chars().filter(ch -> ch == '$').count();
        if (dollars % 2 != 0) {
            issues.add("BROKEN_LATEX_DELIMITER");
        }
        if (markdown.contains("\"$") || markdown.contains("$\"") || markdown.contains("@$")) {
            issues.add("LATEX_OCR_ARTIFACTS");
        }
        double missingPageRatio = missingPageRatio(file.blocks());
        if (missingPageRatio > 0.0d) {
            issues.add("PAGE_PROVENANCE_INCOMPLETE");
        }
        double score = Math.max(0.0d, 1.0d
                - (issues.contains("MARKDOWN_BLANK") ? 1.0d : 0.0d)
                - (issues.contains("FRAGMENTED_SHORT_LINES") ? 0.25d : 0.0d)
                - (issues.contains("OCR_LATIN_NOISE") ? 0.20d : 0.0d)
                - (issues.contains("BROKEN_LATEX_DELIMITER") ? 0.20d : 0.0d)
                - (issues.contains("LATEX_OCR_ARTIFACTS") ? 0.15d : 0.0d)
                - (issues.contains("PAGE_PROVENANCE_INCOMPLETE") ? 0.15d : 0.0d));
        String pageStatus = missingPageRatio == 0.0d ? "VALID" : "REVIEW_REQUIRED";
        return new MarkdownQualityAssessment(score >= mathQualityGateMinScore && !issues.contains("MARKDOWN_BLANK"),
                score,
                score >= 0.9d && issues.isEmpty() ? "VALID" : "REVIEW_REQUIRED",
                issues.stream().distinct().toList(),
                pageStatus);
    }

    private boolean looksLikeLatinNoise(String line) {
        String text = line == null ? "" : line.trim();
        if (text.length() < 2 || text.length() > 40) {
            return false;
        }
        boolean hasHangul = text.chars().anyMatch(ch -> ch >= 0xAC00 && ch <= 0xD7A3);
        boolean hasMath = text.chars().anyMatch(ch -> "0123456789=+-*/^$".indexOf(ch) >= 0);
        if (hasHangul || hasMath) {
            return false;
        }
        long letters = text.chars().filter(Character::isLetter).count();
        return letters >= 2 && text.matches("[A-Za-z\\s!|/\\\\.,:;~_-]+");
    }

    private double missingPageRatio(List<ParsedBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return 0.0d;
        }
        long missing = blocks.stream()
                .filter(block -> block.page() == null && !hasPageSourceRef(block.sourceRef())
                        && !hasPageSourceRef(String.valueOf(block.metadata().getOrDefault("sourceRef", ""))))
                .count();
        return (double) missing / blocks.size();
    }

    private boolean hasPageSourceRef(String sourceRef) {
        return sourceRef != null && sourceRef.matches(".*page\\[\\d+].*");
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private ParsedFile withFallbackWarning(ParsedFile file, String code, Exception failure) {
        Map<String, Object> metadata = new LinkedHashMap<>(file.metadata());
        metadata.put(KEY_EXTRACTION_ENGINE, "pdfbox");
        metadata.put(KEY_FALLBACK_FROM, "pymupdf4llm");
        metadata.put(KEY_PYMUPDF_STATUS, failure == null ? "UNAVAILABLE" : "FAILED");
        metadata.put(KEY_FALLBACK_REASON, code);
        metadata.put("fallbackApplied", true);
        metadata.put("fallbackFrom", "pymupdf4llm");
        metadata.put("fallbackTo", "pdfbox");
        metadata.put("fallbackReason", code);

        Map<String, Object> warningMetadata = new LinkedHashMap<>();
        warningMetadata.put("fallbackEngine", "pdfbox");
        warningMetadata.put("failedEngine", "pymupdf4llm");
        if (failure != null) {
            warningMetadata.put("failureType", failure.getClass().getSimpleName());
            String message = failure.getMessage();
            if (message != null && !message.isBlank()) {
                String error = safeDiagnosticMessage(message);
                warningMetadata.put("failureMessage", error);
                metadata.put(KEY_PYMUPDF_ERROR, error);
            }
        }

        List<ParseWarning> warnings = new ArrayList<>();
        warnings.add(ParseWarning.warning(
                code,
                "PyMuPDF4LLM PDF extraction failed or was unavailable; PDFBox fallback was used.",
                "document",
                warningMetadata));
        warnings.addAll(file.warnings());

        return copy(file, metadata, warnings);
    }

    private ParsedFile withPyMuPdfStatus(ParsedFile file, String status, String error) {
        Map<String, Object> metadata = new LinkedHashMap<>(file.metadata());
        metadata.put(KEY_PYMUPDF_STATUS, status);
        metadata.put("baselineEngine", "pymupdf4llm");
        if (error != null && !error.isBlank()) {
            metadata.put(KEY_PYMUPDF_ERROR, abbreviate(error));
        }
        return copy(file, metadata, file.warnings());
    }

    private String abbreviate(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= 240 ? trimmed : trimmed.substring(0, 237) + "...";
    }

    private String safeDiagnosticMessage(String message) {
        String sanitized = message == null ? "" : message;
        int responseBody = sanitized.indexOf(" body=");
        if (responseBody >= 0) {
            sanitized = sanitized.substring(0, responseBody);
        }
        return abbreviate(sanitized);
    }

    private record MarkdownQualityAssessment(
            boolean accepted,
            double score,
            String status,
            List<String> issues,
            String pageProvenanceStatus) {
    }
}
