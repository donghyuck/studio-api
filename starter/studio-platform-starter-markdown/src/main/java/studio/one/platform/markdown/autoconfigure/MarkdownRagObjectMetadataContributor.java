package studio.one.platform.markdown.autoconfigure;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

import studio.one.platform.ai.service.pipeline.RagObjectMetadataContributor;
import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.documentmetadata.DocumentMetadataProjectionPolicy;
import studio.one.platform.markdown.application.MarkdownDocumentMetadataService;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownPipelineExecution;
import studio.one.platform.markdown.domain.MarkdownRevision;

final class MarkdownRagObjectMetadataContributor implements RagObjectMetadataContributor {
    private final MarkdownRepository repository;
    private final ObjectMapper objectMapper;

    MarkdownRagObjectMetadataContributor(MarkdownRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public Map<String, Object> contribute(String objectType, String objectId) {
        if (!"attachment".equalsIgnoreCase(objectType)) {
            return Map.of();
        }
        long attachmentId;
        try {
            attachmentId = Long.parseLong(objectId);
        } catch (NumberFormatException ex) {
            return Map.of();
        }
        MarkdownDocument document = repository.findDocumentBySourceAttachmentId(attachmentId).orElse(null);
        if (document == null) {
            return Map.of("markdown", Map.of("exists", false));
        }
        MarkdownRevision revision = document.currentRevisionId() == null
                ? null
                : repository.findRevision(document.currentRevisionId())
                        .orElseGet(() -> repository.findRevisions(document.documentId()).stream()
                                .filter(candidate -> document.currentRevisionId().equals(candidate.revisionId()))
                                .findFirst()
                                .orElse(null));
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("exists", true);
        markdown.put("documentId", document.documentId());
        markdown.put("currentRevisionId", document.currentRevisionId());
        if (revision != null) {
            markdown.put("revisionId", revision.revisionId());
            markdown.put("revisionStatus", revision.status().name());
            markdown.put("sourceFormat", revision.sourceFormat());
            markdown.put("updatedAt", revision.updatedAt());
            repository.findResource(revision.revisionId(), MarkdownDocumentMetadataService.RESOURCE_TYPE)
                    .ifPresent(resource -> {
                        try {
                            DocumentMetadataArtifact artifact = objectMapper.readValue(
                                    resource.metadataJson(), DocumentMetadataArtifact.class);
                            markdown.put("documentMetadata",
                                    new DocumentMetadataProjectionPolicy().compact(artifact));
                        } catch (Exception ignored) {
                            markdown.put("documentMetadataStatus", "INVALID");
                        }
                    });
            MarkdownPipelineExecution pipeline =
                    repository.findPipelineExecution(revision.revisionId()).orElse(null);
            if (pipeline != null) {
                markdown.put("pipelineStatus", pipeline.status().name());
                markdown.put("pipelineStage", pipeline.currentStage().name());
                markdown.put("lastCompletedStage", pipeline.lastCompletedStage() == null
                        ? null : pipeline.lastCompletedStage().name());
                markdown.put("attemptCount", pipeline.attemptCount());
                markdown.put("errorCode", pipeline.errorCode());
                markdown.put("errorMessage", pipeline.errorMessage());
            }
        }
        return Map.of("markdown", markdown);
    }
}
