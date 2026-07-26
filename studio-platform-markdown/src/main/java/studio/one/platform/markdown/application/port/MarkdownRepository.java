package studio.one.platform.markdown.application.port;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownExtractPart;
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

    default int recoverStalePipelineExecutions(Instant staleBefore, Instant now) {
        return 0;
    }

    Optional<MarkdownRevision> findActiveRevisionBySourceAttachmentId(long sourceAttachmentId);

    Optional<MarkdownRevision> findReusableRevision(long sourceAttachmentId, String sourceContentHash,
            String extractorType, String extractorVersion, String optionsHash);

    List<MarkdownRevision> findRevisions(String documentId);

    void replaceLocators(String revisionId, List<MarkdownLocator> locators);

    void replaceResources(String revisionId, List<MarkdownResource> resources);

    void replaceExtractParts(String revisionId, List<MarkdownExtractPart> parts);

    void deleteExtractParts(String revisionId);

    void saveExtractPart(MarkdownExtractPart part);

    List<MarkdownLocator> findLocators(String revisionId);

    List<MarkdownResource> findResources(String revisionId);

    default Optional<MarkdownResource> findResource(String revisionId, String resourceType) {
        return findResources(revisionId).stream()
                .filter(resource -> resourceType.equals(resource.resourceType()))
                .findFirst();
    }

    default void upsertResource(MarkdownResource resource) {
        List<MarkdownResource> resources = new java.util.ArrayList<>(findResources(resource.revisionId()));
        resources.removeIf(existing -> existing.resourceId().equals(resource.resourceId())
                || existing.resourceType().equals(resource.resourceType()));
        resources.add(resource);
        replaceResources(resource.revisionId(), resources);
    }

    List<MarkdownExtractPart> findExtractParts(String revisionId);
}
