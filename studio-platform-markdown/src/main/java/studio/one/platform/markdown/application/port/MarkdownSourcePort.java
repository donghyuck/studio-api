package studio.one.platform.markdown.application.port;

public interface MarkdownSourcePort {

    MarkdownSource load(long attachmentId);

    default MarkdownSourceDescriptor describe(long attachmentId) {
        MarkdownSource source = load(attachmentId);
        return new MarkdownSourceDescriptor(source.attachmentId(), source.fileName(), source.contentType(),
                source.objectType(), source.objectId(), source.content() == null ? 0L : source.content().length);
    }

    record MarkdownSource(
            long attachmentId,
            String fileName,
            String contentType,
            String objectType,
            String objectId,
            byte[] content) {
    }

    record MarkdownSourceDescriptor(
            long attachmentId,
            String fileName,
            String contentType,
            String objectType,
            String objectId,
            long size) {
    }
}
