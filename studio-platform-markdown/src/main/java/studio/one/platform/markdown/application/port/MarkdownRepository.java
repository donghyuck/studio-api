package studio.one.platform.markdown.application.port;

import java.util.List;
import java.util.Optional;

import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.markdown.domain.MarkdownPipelineExecution;
import studio.one.platform.markdown.domain.MarkdownResource;
import studio.one.platform.markdown.domain.MarkdownRevision;

public interface MarkdownRepository {

    MarkdownDocument saveDocument(MarkdownDocument document);

    MarkdownRevision saveRevision(MarkdownRevision revision);

    MarkdownPipelineExecution savePipelineExecution(MarkdownPipelineExecution execution);

    Optional<MarkdownDocument> findDocument(String documentId);

    Optional<MarkdownDocument> findDocumentBySourceAttachmentId(long sourceAttachmentId);

    Optional<MarkdownRevision> findRevision(String revisionId);

    Optional<MarkdownRevision> findRevisionByConvertJobId(String convertJobId);

    Optional<MarkdownPipelineExecution> findPipelineExecution(String revisionId);

    Optional<MarkdownRevision> findActiveRevisionBySourceAttachmentId(long sourceAttachmentId);

    Optional<MarkdownRevision> findReusableRevision(long sourceAttachmentId, String sourceContentHash,
            String extractorType, String extractorVersion, String optionsHash);

    List<MarkdownRevision> findRevisions(String documentId);

    void replaceLocators(String revisionId, List<MarkdownLocator> locators);

    void replaceResources(String revisionId, List<MarkdownResource> resources);

    List<MarkdownLocator> findLocators(String revisionId);

    List<MarkdownResource> findResources(String revisionId);
}
