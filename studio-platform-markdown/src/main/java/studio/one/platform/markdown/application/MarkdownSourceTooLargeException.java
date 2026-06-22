package studio.one.platform.markdown.application;

public class MarkdownSourceTooLargeException extends RuntimeException {
    private final long actualBytes;
    private final long maxBytes;

    public MarkdownSourceTooLargeException(long actualBytes, long maxBytes) {
        super("Attachment exceeds markdown source size limit");
        this.actualBytes = actualBytes;
        this.maxBytes = maxBytes;
    }

    public long actualBytes() {
        return actualBytes;
    }

    public long maxBytes() {
        return maxBytes;
    }
}
