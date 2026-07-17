package studio.one.platform.textract.infrastructure.extractor.pdf;

import java.util.Objects;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.ParsedFile;

public class MathDocumentOcrExtractionEngine implements MathDocumentExtractionEngine {

    private final MathDocumentOcrClient client;

    public MathDocumentOcrExtractionEngine(MathDocumentOcrClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public boolean enabled() {
        return client.available();
    }

    @Override
    public boolean supports(PdfExtractionRequest request, PdfDocumentAnalysis analysis) {
        return client.available()
                && request != null
                && analysis != null
                && analysis.mathLike()
                && isPdf(request);
    }

    @Override
    public ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis analysis) throws FileParseException {
        return client.extract(request, analysis);
    }

    private boolean isPdf(PdfExtractionRequest request) {
        String contentType = request.contentType();
        if (contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("pdf")) {
            return true;
        }
        String filename = request.filename();
        return filename != null && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
    }
}
