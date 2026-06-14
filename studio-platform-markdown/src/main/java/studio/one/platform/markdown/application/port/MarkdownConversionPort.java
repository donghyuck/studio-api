package studio.one.platform.markdown.application.port;

public interface MarkdownConversionPort {

    ConversionSubmission submit(String jobId, long sourceAttachmentId, String sourceFormat, String requestedBy);

    void cancel(String jobId);

    record ConversionSubmission(
            String jobId,
            String status,
            String errorCode,
            String errorMessage) {
    }
}
