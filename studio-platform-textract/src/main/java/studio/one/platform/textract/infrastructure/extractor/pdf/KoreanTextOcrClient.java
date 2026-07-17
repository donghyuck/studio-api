package studio.one.platform.textract.infrastructure.extractor.pdf;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.ParsedFile;

/** Optional page-scoped OCR provider for Korean prose. */
public interface KoreanTextOcrClient {

    boolean available();

    String provider();

    default int maxPagesPerRequest() {
        return 1;
    }

    ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis analysis) throws FileParseException;
}
