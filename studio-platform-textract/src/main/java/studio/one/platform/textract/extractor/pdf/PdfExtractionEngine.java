package studio.one.platform.textract.extractor.pdf;

import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.model.ParsedFile;

public interface PdfExtractionEngine {

    PdfExtractionEngineType type();

    boolean supports(PdfExtractionRequest request);

    ParsedFile extract(PdfExtractionRequest request) throws FileParseException;
}
