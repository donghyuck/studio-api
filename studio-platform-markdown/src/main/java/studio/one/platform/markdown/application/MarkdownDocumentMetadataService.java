package studio.one.platform.markdown.application;

import java.util.Objects;

import tools.jackson.databind.ObjectMapper;

import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.documentmetadata.DocumentMetadataProjectionPolicy;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownResource;
import studio.one.platform.markdown.domain.MarkdownRevision;

public class MarkdownDocumentMetadataService {

    public static final String RESOURCE_TYPE = "DOCUMENT_METADATA";
    public static final String RESOURCE_NAME = "document-metadata.json";

    private final MarkdownRepository repository;
    private final ObjectMapper objectMapper;
    private final DocumentMetadataProjectionPolicy projectionPolicy;

    public MarkdownDocumentMetadataService(MarkdownRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, new DocumentMetadataProjectionPolicy());
    }

    MarkdownDocumentMetadataService(
            MarkdownRepository repository,
            ObjectMapper objectMapper,
            DocumentMetadataProjectionPolicy projectionPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.projectionPolicy = projectionPolicy == null ? new DocumentMetadataProjectionPolicy() : projectionPolicy;
    }

    public DocumentMetadataArtifact get(String documentId, String requestedRevisionId) {
        return load(documentId, requestedRevisionId);
    }

    public DocumentMetadataSummaryView getSummary(String documentId, String requestedRevisionId) {
        return DocumentMetadataSummaryView.from(
                documentId,
                load(documentId, requestedRevisionId),
                projectionPolicy);
    }

    private DocumentMetadataArtifact load(String documentId, String requestedRevisionId) {
        MarkdownDocument document = repository.findDocument(documentId)
                .orElseThrow(() -> new MarkdownDocumentNotFoundException("Markdown document not found: " + documentId));
        String revisionId = hasText(requestedRevisionId) ? requestedRevisionId : document.currentRevisionId();
        if (!hasText(revisionId)) {
            throw new MarkdownDocumentNotFoundException("Markdown document has no current revision: " + documentId);
        }
        MarkdownRevision revision = repository.findRevision(revisionId)
                .orElseThrow(() -> new MarkdownDocumentNotFoundException("Markdown revision not found: " + revisionId));
        if (!documentId.equals(revision.documentId())) {
            throw new MarkdownDocumentNotFoundException(
                    "Markdown revision does not belong to document: " + revisionId);
        }
        MarkdownResource resource = repository.findResource(revisionId, RESOURCE_TYPE)
                .orElseThrow(() -> new MarkdownDocumentNotFoundException(
                        "Document metadata not found for revision: " + revisionId));
        try {
            return objectMapper.readValue(resource.metadataJson(), DocumentMetadataArtifact.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Stored document metadata is invalid: " + revisionId, ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
