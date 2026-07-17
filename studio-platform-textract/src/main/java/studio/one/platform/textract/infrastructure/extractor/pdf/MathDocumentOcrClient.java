package studio.one.platform.textract.infrastructure.extractor.pdf;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.ParsedFile;

public interface MathDocumentOcrClient {

    boolean available();

    String provider();

    ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis analysis) throws FileParseException;
}
