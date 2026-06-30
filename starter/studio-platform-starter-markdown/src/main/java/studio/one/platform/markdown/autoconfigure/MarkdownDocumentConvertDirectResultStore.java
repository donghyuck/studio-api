package studio.one.platform.markdown.autoconfigure;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import studio.one.platform.documentconvert.application.port.out.DocumentConvertDirectResultStore;
import studio.one.platform.documentconvert.domain.model.DocumentConvertJob;
import studio.one.platform.documentconvert.domain.type.DocumentFormat;
import studio.one.platform.markdown.application.MarkdownDocumentService;

public class MarkdownDocumentConvertDirectResultStore implements DocumentConvertDirectResultStore {

    private final MarkdownDocumentService service;
    private final long maxResultBytes;

    public MarkdownDocumentConvertDirectResultStore(MarkdownDocumentService service, long maxResultBytes) {
        this.service = service;
        this.maxResultBytes = Math.max(1L, maxResultBytes);
    }

    @Override
    public boolean supports(DocumentConvertJob job) {
        return job != null
                && job.targetFormat() == DocumentFormat.MARKDOWN
                && job.jobId() != null
                && job.jobId().startsWith("conv-md-");
    }

    @Override
    public String storeResult(DocumentConvertJob job, InputStream input) {
        String markdown = readMarkdown(input);
        service.onConversionResult(job.jobId(), markdown, parseLong(job.sourceFileId()));
        return job.jobId();
    }

    private String readMarkdown(InputStream input) {
        try (InputStream in = input) {
            byte[] bytes = in.readNBytes(Math.toIntExact(Math.min(Integer.MAX_VALUE, maxResultBytes + 1L)));
            if (bytes.length > maxResultBytes) {
                throw new IllegalArgumentException("Converted markdown exceeds max result size");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read converted markdown", ex);
        }
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
