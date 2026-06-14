package studio.one.platform.markdown.application.port;

public interface MarkdownSourcePort {

    MarkdownSource load(long attachmentId);

    record MarkdownSource(
            long attachmentId,
            String fileName,
            String contentType,
            String objectType,
            String objectId,
            byte[] content) {
    }
}
