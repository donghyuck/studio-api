package studio.one.platform.textract.extractor.impl;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.MediaType;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.StructuredFileParser;
import studio.one.platform.textract.extractor.pdf.PdfExtractionEngineSelector;
import studio.one.platform.textract.extractor.pdf.PdfExtractionMode;
import studio.one.platform.textract.extractor.pdf.PdfExtractionOptions;
import studio.one.platform.textract.extractor.pdf.PdfExtractionRequest;
import studio.one.platform.textract.extractor.pdf.pdfbox.PdfBoxExtractionEngine;
import studio.one.platform.textract.model.ParsedFile;

@Slf4j
public class PdfFileParser extends AbstractFileParser implements StructuredFileParser {

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
        return pdf && selector.supports(new PdfExtractionRequest(new byte[0], contentType, filename, options));
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
        if (options.engine() != PdfExtractionMode.AUTO
                || !options.pyMuPdf4LlmEnabled()
                || options.preferPyMuPdf4LlmMinPages() <= 0) {
            return options;
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return options.withPageCount(document.getNumberOfPages());
        } catch (IOException ex) {
            log.debug("Failed to inspect PDF page count before engine selection.", ex);
            return options;
        }
    }
}
