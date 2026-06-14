package studio.one.platform.markdown.application;

import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownRevision;

public record MarkdownExtractionResult(
        MarkdownDocument document,
        MarkdownRevision revision,
        boolean reused) {
}
