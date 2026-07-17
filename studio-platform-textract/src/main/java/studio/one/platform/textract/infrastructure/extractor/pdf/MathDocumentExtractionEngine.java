package studio.one.platform.textract.infrastructure.extractor.pdf;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.ParsedFile;

public interface MathDocumentExtractionEngine {

    default boolean enabled() {
        return false;
    }

    boolean supports(PdfExtractionRequest request, PdfDocumentAnalysis analysis);

    ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis analysis) throws FileParseException;
}
