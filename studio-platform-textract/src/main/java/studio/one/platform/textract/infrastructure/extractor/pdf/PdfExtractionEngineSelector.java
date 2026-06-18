package studio.one.platform.textract.infrastructure.extractor.pdf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.ParseWarning;
import studio.one.platform.textract.domain.model.ParsedBlock;
import studio.one.platform.textract.domain.model.ParsedFile;

public class PdfExtractionEngineSelector {

    public static final String KEY_EXTRACTION_ENGINE = "pdfExtractionEngine";
    public static final String KEY_FALLBACK_FROM = "pdfExtractionFallbackFrom";
    public static final String KEY_LARGE_PDF_EXTRACTION = "pdfLargeExtraction";
    public static final String KEY_LARGE_PDF_PARTS = "pdfExtractionParts";
    public static final String KEY_LARGE_PDF_TEXT_LENGTH = "pdfLargeExtractionTextLength";

    private final List<PdfExtractionEngine> engines;

    public PdfExtractionEngineSelector(List<PdfExtractionEngine> engines) {
        this.engines = engines == null ? List.of() : List.copyOf(engines);
    }

    public ParsedFile extract(PdfExtractionRequest request) throws FileParseException {
        PdfExtractionOptions options = request.options();
        return switch (options.engine()) {
            case PDFBOX -> extractRequired(request, PdfExtractionEngineType.PDFBOX);
            case PYMUPDF4LLM -> extractPyMuPdfFirst(request);
            case AUTO -> extractAuto(request);
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

    private ParsedFile extractAuto(PdfExtractionRequest request) throws FileParseException {
        if (request.options().prefersPyMuPdf4Llm()) {
            return extractPyMuPdfFirst(request);
        }
        PdfExtractionEngine pdfBox = find(PdfExtractionEngineType.PDFBOX);
        if (pdfBox != null && pdfBox.supports(request)) {
            return pdfBox.extract(request);
        }
        return extractRequired(request, PdfExtractionEngineType.PYMUPDF4LLM);
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
                return extractLargePdfInParts(request, pyMuPdf);
            }
            return pyMuPdf.extract(request);
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
                        0, null, "PYMUPDF4LLM_FAILED", abbreviate(ex.getMessage()), elapsedMs, Map.of());
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

        Map<String, Object> warningMetadata = new LinkedHashMap<>();
        warningMetadata.put("fallbackEngine", "pdfbox");
        warningMetadata.put("failedEngine", "pymupdf4llm");
        if (failure != null) {
            warningMetadata.put("failureType", failure.getClass().getSimpleName());
            String message = failure.getMessage();
            if (message != null && !message.isBlank()) {
                warningMetadata.put("failureMessage", abbreviate(message));
            }
        }

        List<ParseWarning> warnings = new ArrayList<>();
        warnings.add(ParseWarning.warning(
                code,
                "PyMuPDF4LLM PDF extraction failed or was unavailable; PDFBox fallback was used.",
                "document",
                warningMetadata));
        warnings.addAll(file.warnings());

        return new ParsedFile(
                file.format(),
                file.plainText(),
                file.blocks(),
                metadata,
                warnings,
                file.pages(),
                file.tables(),
                file.images(),
                file.ocrApplied());
    }

    private String abbreviate(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= 240 ? trimmed : trimmed.substring(0, 237) + "...";
    }
}
