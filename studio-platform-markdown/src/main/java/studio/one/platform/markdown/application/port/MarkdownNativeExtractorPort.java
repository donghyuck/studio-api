package studio.one.platform.markdown.application.port;

import java.util.List;

import studio.one.platform.markdown.domain.MarkdownExtractPart;
import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.markdown.domain.MarkdownResource;

public interface MarkdownNativeExtractorPort {

    NativeExtraction extract(MarkdownSourcePort.MarkdownSource source, String revisionId);

    record NativeExtraction(
            String markdown,
            String extractorVersion,
            List<MarkdownLocator> locators,
            List<MarkdownResource> resources,
            List<MarkdownExtractPart> extractParts,
            String errorCode,
            String errorMessage) {

        public NativeExtraction(
                String markdown,
                String extractorVersion,
                List<MarkdownLocator> locators,
                List<MarkdownResource> resources) {
            this(markdown, extractorVersion, locators, resources, List.of(), null, null);
        }

        public NativeExtraction {
            locators = locators == null ? List.of() : List.copyOf(locators);
            resources = resources == null ? List.of() : List.copyOf(resources);
            extractParts = extractParts == null ? List.of() : List.copyOf(extractParts);
        }
    }
}
