package studio.one.platform.textract.infrastructure.extractor.pdf.pymupdf;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionRequest;

public interface PyMuPdf4LlmClient {

    PyMuPdf4LlmResponse extract(PdfExtractionRequest request) throws FileParseException;
}
