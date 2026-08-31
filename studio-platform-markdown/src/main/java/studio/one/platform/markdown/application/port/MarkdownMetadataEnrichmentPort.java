package studio.one.platform.markdown.application.port;

import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.domain.MarkdownRevision;

@FunctionalInterface
public interface MarkdownMetadataEnrichmentPort {

    void enrich(MarkdownRevision revision, MarkdownPipelineOptions options);

    default DocumentMetadataArtifact preview(MarkdownRevision revision, MarkdownPipelineOptions options) {
        throw new UnsupportedOperationException("Metadata preview is not supported");
    }

    default DocumentMetadataArtifact regenerate(MarkdownRevision revision, MarkdownPipelineOptions options) {
        throw new UnsupportedOperationException("Metadata regeneration is not supported");
    }

    static MarkdownMetadataEnrichmentPort noop() {
        return (revision, options) -> {
        };
    }
}
