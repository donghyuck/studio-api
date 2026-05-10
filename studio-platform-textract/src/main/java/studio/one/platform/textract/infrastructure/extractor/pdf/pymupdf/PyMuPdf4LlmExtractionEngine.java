package studio.one.platform.textract.infrastructure.extractor.pdf.pymupdf;

import java.util.Objects;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionEngine;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionEngineType;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionRequest;
import studio.one.platform.textract.domain.model.ParsedFile;

public class PyMuPdf4LlmExtractionEngine implements PdfExtractionEngine {

    private final PyMuPdf4LlmClient client;
    private final PyMuPdf4LlmResultMapper mapper;

    public PyMuPdf4LlmExtractionEngine(PyMuPdf4LlmClient client) {
        this(client, new PyMuPdf4LlmResultMapper());
    }

    public PyMuPdf4LlmExtractionEngine(PyMuPdf4LlmClient client, PyMuPdf4LlmResultMapper mapper) {
        this.client = Objects.requireNonNull(client, "client");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public PdfExtractionEngineType type() {
        return PdfExtractionEngineType.PYMUPDF4LLM;
    }

    @Override
    public boolean supports(PdfExtractionRequest request) {
        return request.options().pyMuPdf4LlmEnabled()
                && request.bytes().length <= request.options().pyMuPdf4LlmMaxFileSizeBytes();
    }

    @Override
    public ParsedFile extract(PdfExtractionRequest request) throws FileParseException {
        return mapper.map(client.extract(request), request);
    }
}
