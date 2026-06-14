package studio.one.platform.markdown.application.port;

import java.util.List;

import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.markdown.domain.MarkdownResource;

public interface MarkdownNativeExtractorPort {

    NativeExtraction extract(MarkdownSourcePort.MarkdownSource source, String revisionId);

    record NativeExtraction(
            String markdown,
            String extractorVersion,
            List<MarkdownLocator> locators,
            List<MarkdownResource> resources) {
    }
}
