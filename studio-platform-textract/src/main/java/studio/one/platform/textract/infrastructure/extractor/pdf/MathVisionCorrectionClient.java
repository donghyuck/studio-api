package studio.one.platform.textract.infrastructure.extractor.pdf;

import java.util.List;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.ParsedFile;

public interface MathVisionCorrectionClient {

    default boolean available() {
        return false;
    }

    String provider();

    ParsedFile correct(PdfExtractionRequest request, PdfDocumentAnalysis analysis, List<Integer> pages)
            throws FileParseException;
}
