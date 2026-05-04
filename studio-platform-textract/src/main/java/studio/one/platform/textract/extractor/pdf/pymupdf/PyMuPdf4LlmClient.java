package studio.one.platform.textract.extractor.pdf.pymupdf;

import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.pdf.PdfExtractionRequest;

public interface PyMuPdf4LlmClient {

    PyMuPdf4LlmResponse extract(PdfExtractionRequest request) throws FileParseException;
}
