package studio.one.platform.textract.autoconfigure;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.ParsedFile;
import studio.one.platform.textract.infrastructure.extractor.pdf.KoreanTextOcrClient;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfDocumentAnalysis;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionEngineSelector;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionRequest;
import studio.one.platform.textract.infrastructure.extractor.pdf.pymupdf.PyMuPdf4LlmResultMapper;

/**
 * Adapter for self-hosted Korean OCR workers that expose the structured PDF
 * worker response contract used by the PyMuPDF worker.
 */
class PaddleOcrKoreanTextClient implements KoreanTextOcrClient {

    private final boolean enabled;
    private final String provider;
    private final int maxPagesPerRequest;
    private final PyMuPdf4LlmHttpClient workerClient;
    private final PyMuPdf4LlmResultMapper mapper = new PyMuPdf4LlmResultMapper();

    PaddleOcrKoreanTextClient(boolean enabled, String provider, String endpoint, Duration timeout,
            int maxFileSizeBytes, int maxPagesPerRequest, ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.provider = provider == null || provider.isBlank() ? "paddleocr" : provider;
        this.maxPagesPerRequest = Math.max(1, maxPagesPerRequest);
        this.workerClient = new PyMuPdf4LlmHttpClient(endpoint, timeout, maxFileSizeBytes, objectMapper);
    }

    @Override
    public boolean available() {
        return enabled;
    }

    @Override
    public String provider() {
        return provider;
    }

    @Override
    public int maxPagesPerRequest() {
        return maxPagesPerRequest;
    }

    @Override
    public ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis analysis) throws FileParseException {
        ParsedFile extracted = mapper.map(workerClient.extract(request), request);
        Map<String, Object> metadata = new LinkedHashMap<>(extracted.metadata());
        metadata.put(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, provider);
        metadata.put("extractionEngine", provider);
        metadata.put("koreanTextOcrProvider", provider);
        metadata.put("pageFrom", request.options().pageFrom());
        metadata.put("pageTo", request.options().pageTo());
        return new ParsedFile(extracted.format(), extracted.plainText(), extracted.blocks(), metadata,
                extracted.warnings(), extracted.pages(), extracted.tables(), extracted.images(), true,
                extracted.markdown(), extracted.contentFormat(), extracted.locators());
    }
}
