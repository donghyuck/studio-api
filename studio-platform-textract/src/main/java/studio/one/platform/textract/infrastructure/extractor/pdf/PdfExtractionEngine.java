package studio.one.platform.textract.infrastructure.extractor.pdf;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.ParsedFile;

public interface PdfExtractionEngine {

    PdfExtractionEngineType type();

    boolean supports(PdfExtractionRequest request);

    ParsedFile extract(PdfExtractionRequest request) throws FileParseException;
}
