package studio.one.platform.markdown.autoconfigure;

import studio.one.platform.documentconvert.application.port.out.DocumentConvertJobListener;
import studio.one.platform.documentconvert.domain.model.DocumentConvertJob;
import studio.one.platform.markdown.application.MarkdownDocumentService;

public class MarkdownDocumentConvertListener implements DocumentConvertJobListener {
    private final MarkdownDocumentService service;

    public MarkdownDocumentConvertListener(MarkdownDocumentService service) {
        this.service = service;
    }

    @Override
    public void onCompleted(DocumentConvertJob job) {
        Long resultAttachmentId = parseLong(job.resultFileId());
        if (resultAttachmentId == null) {
            return;
        }
        service.onConversionCompleted(job.jobId(), resultAttachmentId, parseLong(job.sourceFileId()));
    }

    @Override
    public void onFailed(DocumentConvertJob job) {
        service.onConversionFailed(job.jobId(), parseLong(job.sourceFileId()), job.errorCode(), job.errorMessage());
    }

    @Override
    public void onCanceled(DocumentConvertJob job) {
        service.onConversionCanceled(job.jobId(), parseLong(job.sourceFileId()));
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.startsWith("att-") ? value.substring(4) : value;
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
