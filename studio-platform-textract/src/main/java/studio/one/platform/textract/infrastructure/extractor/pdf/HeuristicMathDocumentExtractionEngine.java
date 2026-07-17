package studio.one.platform.textract.infrastructure.extractor.pdf;

import java.util.Objects;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.ParsedFile;

public class HeuristicMathDocumentExtractionEngine implements MathDocumentExtractionEngine {

    private final PdfExtractionEngine delegate;
    private final MathMarkdownPostProcessor postProcessor;

    public HeuristicMathDocumentExtractionEngine(PdfExtractionEngine delegate) {
        this(delegate, new MathMarkdownPostProcessor());
    }

    public HeuristicMathDocumentExtractionEngine(
            PdfExtractionEngine delegate,
            MathMarkdownPostProcessor postProcessor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.postProcessor = postProcessor == null ? new MathMarkdownPostProcessor() : postProcessor;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public boolean supports(PdfExtractionRequest request, PdfDocumentAnalysis analysis) {
        return analysis != null && analysis.mathLike() && delegate.supports(request);
    }

    @Override
    public ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis analysis) throws FileParseException {
        PdfExtractionRequest effectiveRequest = request;
        if (analysis != null && analysis.ocrRecommended() && !request.options().ocrRequired()) {
            effectiveRequest = new PdfExtractionRequest(
                    request.bytes(),
                    request.contentType(),
                    request.filename(),
                    request.options().withOcrRequired(true));
        }
        return postProcessor.process(delegate.extract(effectiveRequest));
    }
}
