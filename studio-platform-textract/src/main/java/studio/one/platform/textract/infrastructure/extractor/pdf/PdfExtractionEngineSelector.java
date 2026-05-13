package studio.one.platform.textract.infrastructure.extractor.pdf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.ParseWarning;
import studio.one.platform.textract.domain.model.ParsedFile;

public class PdfExtractionEngineSelector {

    public static final String KEY_EXTRACTION_ENGINE = "pdfExtractionEngine";
    public static final String KEY_FALLBACK_FROM = "pdfExtractionFallbackFrom";

    private final List<PdfExtractionEngine> engines;

    public PdfExtractionEngineSelector(List<PdfExtractionEngine> engines) {
        this.engines = engines == null ? List.of() : List.copyOf(engines);
    }

    public ParsedFile extract(PdfExtractionRequest request) throws FileParseException {
        PdfExtractionOptions options = request.options();
        switch (options.engine()) {
            case PDFBOX:
                return extractRequired(request, PdfExtractionEngineType.PDFBOX);
            case PYMUPDF4LLM:
                return extractPyMuPdfFirst(request);
            case AUTO:
            default:
                return extractAuto(request);
        }
    }

    public boolean supports(PdfExtractionRequest request) {
        PdfExtractionOptions options = request.options();
        switch (options.engine()) {
            case PDFBOX:
                return supports(request, PdfExtractionEngineType.PDFBOX);
            case PYMUPDF4LLM:
                return supports(request, PdfExtractionEngineType.PYMUPDF4LLM)
                        || (options.fallbackEnabled() && supports(request, PdfExtractionEngineType.PDFBOX));
            case AUTO:
            default:
                return supports(request, PdfExtractionEngineType.PDFBOX)
                        || supports(request, PdfExtractionEngineType.PYMUPDF4LLM);
        }
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
