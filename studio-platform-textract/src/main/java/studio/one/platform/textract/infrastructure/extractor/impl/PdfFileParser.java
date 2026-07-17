package studio.one.platform.textract.infrastructure.extractor.impl;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.application.usecase.StructuredFileParser;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionEngineSelector;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionMode;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionOptions;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionOptionsContext;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionRequest;
import studio.one.platform.textract.infrastructure.extractor.pdf.pdfbox.PdfBoxExtractionEngine;
import studio.one.platform.textract.domain.model.ParsedFile;

public class PdfFileParser extends AbstractFileParser implements StructuredFileParser {

    private static final Logger log = LoggerFactory.getLogger(PdfFileParser.class);

    private final PdfExtractionEngineSelector selector;
    private final PdfExtractionOptions options;
    private final PdfBoxExtractionEngine defaultPdfBoxEngine;

    public PdfFileParser() {
        this(new PdfBoxExtractionEngine());
    }

    private PdfFileParser(PdfBoxExtractionEngine pdfBoxEngine) {
        this(new PdfExtractionEngineSelector(List.of(pdfBoxEngine)), PdfExtractionOptions.defaults(), pdfBoxEngine);
    }

    public PdfFileParser(PdfExtractionEngineSelector selector, PdfExtractionOptions options) {
        this(selector, options, new PdfBoxExtractionEngine());
    }

    private PdfFileParser(
            PdfExtractionEngineSelector selector,
            PdfExtractionOptions options,
            PdfBoxExtractionEngine defaultPdfBoxEngine) {
        this.selector = Objects.requireNonNull(selector, "selector");
        this.options = options == null ? PdfExtractionOptions.defaults() : options;
        this.defaultPdfBoxEngine = Objects.requireNonNull(defaultPdfBoxEngine, "defaultPdfBoxEngine");
    }

    @Override
    public boolean supports(String contentType, String filename) {
        boolean pdf = false;
        try {
            if (contentType != null) {
                MediaType mt = MediaType.parseMediaType(contentType);
                if (MediaType.APPLICATION_PDF.includes(mt)) {
                    pdf = true;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse media type: {}", contentType, e);
        }
        pdf = pdf || hasExtension(filename, ".pdf");
        PdfExtractionOptions effectiveOptions = effectiveOptions();
        return pdf && selector.supports(new PdfExtractionRequest(new byte[0], contentType, filename, effectiveOptions));
    }

    @Override
    public ParsedFile parseStructured(byte[] bytes, String contentType, String filename) throws FileParseException {
        return selector.extract(new PdfExtractionRequest(bytes, contentType, filename, withDetectedPageCount(bytes)));
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        return parseStructured(bytes, contentType, filename).plainText();
    }

    String cleanPdfText(String raw) {
        return defaultPdfBoxEngine.cleanPdfText(raw);
    }

    List<String> cleanPdfPages(List<String> rawPages) {
        return defaultPdfBoxEngine.cleanPdfPages(rawPages);
    }

    private PdfExtractionOptions withDetectedPageCount(byte[] bytes) {
        PdfExtractionOptions effectiveOptions = effectiveOptions();
        if (!effectiveOptions.pyMuPdf4LlmEnabled()
                || (effectiveOptions.engine() == PdfExtractionMode.PDFBOX)
                || (effectiveOptions.engine() == PdfExtractionMode.AUTO
                        && effectiveOptions.preferPyMuPdf4LlmMinPages() <= 0
                        && !effectiveOptions.largePdfEnabled())) {
            return effectiveOptions;
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return effectiveOptions.withPageCount(document.getNumberOfPages());
        } catch (IOException ex) {
            log.debug("Failed to inspect PDF page count before engine selection.", ex);
            return effectiveOptions;
        }
    }

    private PdfExtractionOptions effectiveOptions() {
        PdfExtractionOptions effective = PdfExtractionOptionsContext.ocrRequired()
                .map(options::withOcrRequired)
                .orElse(options);
        effective = PdfExtractionOptionsContext.ocrLanguage()
                .map(effective::withOcrLanguage)
                .orElse(effective);
        effective = PdfExtractionOptionsContext.ocrMode()
                .map(effective::withOcrMode)
                .orElse(effective);
        return PdfExtractionOptionsContext.mathVisionCorrection()
                .map(effective::withMathVisionCorrection)
                .orElse(effective);
    }
}
