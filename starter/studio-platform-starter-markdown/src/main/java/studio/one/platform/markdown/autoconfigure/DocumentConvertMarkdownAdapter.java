package studio.one.platform.markdown.autoconfigure;

import java.util.Map;

import studio.one.platform.documentconvert.application.result.DocumentConvertJobResult;
import studio.one.platform.documentconvert.application.service.DocumentConvertService;
import studio.one.platform.markdown.application.port.MarkdownConversionPort;

public class DocumentConvertMarkdownAdapter implements MarkdownConversionPort {
    private final DocumentConvertService service;

    public DocumentConvertMarkdownAdapter(DocumentConvertService service) {
        this.service = service;
    }

    @Override
    public ConversionSubmission submit(String jobId, long sourceAttachmentId, String sourceFormat,
            String requestedBy) {
        DocumentConvertJobResult result = service.createWithJobId(jobId, Long.toString(sourceAttachmentId),
                sourceFormat, "markdown", Map.of(), requestedBy);
        return new ConversionSubmission(result.jobId(), result.status().name(),
                result.errorCode(), result.errorMessage());
    }

    @Override
    public void cancel(String jobId) {
        service.cancel(jobId);
    }
}
