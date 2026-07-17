package studio.one.platform.markdown.application.port;

import studio.one.platform.markdown.application.MarkdownPagePreviewBounds;
import studio.one.platform.markdown.application.MarkdownPagePreviewUnavailableException;

public interface MarkdownPagePreviewPort {

    byte[] renderPng(MarkdownSourcePort.MarkdownSource source, int page, MarkdownPagePreviewBounds bounds);

    static MarkdownPagePreviewPort unsupported() {
        return (source, page, bounds) -> {
            throw new MarkdownPagePreviewUnavailableException("PDF page preview rendering is not configured");
        };
    }
}
