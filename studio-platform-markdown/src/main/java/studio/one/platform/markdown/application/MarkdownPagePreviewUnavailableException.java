package studio.one.platform.markdown.application;

public class MarkdownPagePreviewUnavailableException extends RuntimeException {

    public MarkdownPagePreviewUnavailableException(String message) {
        super(message);
    }

    public MarkdownPagePreviewUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
