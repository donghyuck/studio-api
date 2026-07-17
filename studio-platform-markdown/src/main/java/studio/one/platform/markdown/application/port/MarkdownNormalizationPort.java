package studio.one.platform.markdown.application.port;

import java.util.List;

import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.markdown.domain.MarkdownResource;

public interface MarkdownNormalizationPort {
    String RESOURCE_TYPE_NORMALIZED_DOCUMENT = "NORMALIZED_DOCUMENT";

    NormalizationResult normalize(NormalizationRequest request);

    static MarkdownNormalizationPort noop() {
        return request -> new NormalizationResult(
                request == null ? "" : request.markdown(),
                request == null ? List.of() : request.locators(),
                request == null ? List.of() : request.resources());
    }

    record NormalizationRequest(
            String revisionId,
            String sourceFormat,
            String sourceFileName,
            String markdown,
            List<MarkdownLocator> locators,
            List<MarkdownResource> resources,
            String normalizationSource,
            String requestedDocumentProfile,
            String resolvedDocumentProfile,
            String documentProfileVersion) {

        public NormalizationRequest(String revisionId, String sourceFormat, String sourceFileName,
                String markdown, List<MarkdownLocator> locators, List<MarkdownResource> resources,
                String normalizationSource) {
            this(revisionId, sourceFormat, sourceFileName, markdown, locators, resources,
                    normalizationSource, null, null, null);
        }

        public NormalizationRequest {
            markdown = markdown == null ? "" : markdown;
            locators = locators == null ? List.of() : List.copyOf(locators);
            resources = resources == null ? List.of() : List.copyOf(resources);
            normalizationSource = normalizationSource == null || normalizationSource.isBlank()
                    ? "MARKDOWN_FALLBACK"
                    : normalizationSource;
            requestedDocumentProfile = blankToNull(requestedDocumentProfile);
            resolvedDocumentProfile = blankToNull(resolvedDocumentProfile);
            documentProfileVersion = blankToNull(documentProfileVersion);
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    record NormalizationResult(
            String markdown,
            List<MarkdownLocator> locators,
            List<MarkdownResource> resources) {

        public NormalizationResult {
            markdown = markdown == null ? "" : markdown;
            locators = locators == null ? List.of() : List.copyOf(locators);
            resources = resources == null ? List.of() : List.copyOf(resources);
        }
    }
}
