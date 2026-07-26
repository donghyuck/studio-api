package studio.one.platform.markdown.autoconfigure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.service.pipeline.RagDocumentMetadataProvider;
import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.documentmetadata.DocumentMetadataEvidence;
import studio.one.platform.documentmetadata.DocumentMetadataField;
import studio.one.platform.documentmetadata.DocumentMetadataProjectionPolicy;
import studio.one.platform.markdown.application.MarkdownDocumentMetadataService;
import studio.one.platform.markdown.application.port.MarkdownRepository;

final class MarkdownRagDocumentMetadataProvider implements RagDocumentMetadataProvider {

    private final MarkdownRepository repository;
    private final ObjectMapper objectMapper;
    private final DocumentMetadataProjectionPolicy projection = new DocumentMetadataProjectionPolicy();

    MarkdownRagDocumentMetadataProvider(MarkdownRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String objectType) {
        return "attachment".equalsIgnoreCase(objectType);
    }

    @Override
    public List<RagSearchResult> find(String objectType, String objectId) {
        if (!supports(objectType) || objectId == null || !objectId.matches("\\d+")) {
            return List.of();
        }
        return repository.findDocumentBySourceAttachmentId(Long.parseLong(objectId))
                .flatMap(document -> document.currentRevisionId() == null
                        ? java.util.Optional.empty()
                        : repository.findResource(
                                document.currentRevisionId(), MarkdownDocumentMetadataService.RESOURCE_TYPE)
                                .map(resource -> new CurrentArtifact(document.documentId(),
                                        document.currentRevisionId(), resource.metadataJson())))
                .map(this::results)
                .orElseGet(List::of);
    }

    private List<RagSearchResult> results(CurrentArtifact current) {
        try {
            DocumentMetadataArtifact artifact = objectMapper.readValue(
                    current.metadataJson(), DocumentMetadataArtifact.class);
            Map<String, DocumentMetadataField> facts = projection.promptFacts(artifact);
            List<RagSearchResult> results = new ArrayList<>();
            facts.forEach((fieldId, field) -> field.evidence().stream().findFirst()
                    .filter(evidence -> evidence.blockId() != null
                            && evidence.exactText() != null
                            && !evidence.exactText().isBlank())
                    .ifPresent(evidence -> results.add(result(current, artifact, fieldId, field, evidence))));
            return List.copyOf(results);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private RagSearchResult result(
            CurrentArtifact current,
            DocumentMetadataArtifact artifact,
            String fieldId,
            DocumentMetadataField field,
            DocumentMetadataEvidence evidence) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("documentId", current.documentId());
        metadata.put("revisionId", current.revisionId());
        metadata.put("chunkId", "metadata:" + fieldId);
        metadata.put("evidenceKind", "DOCUMENT_METADATA");
        metadata.put("supportStatus", "SOURCE_VERIFIED");
        metadata.put("metadataFieldId", fieldId);
        metadata.put("metadataValues", field.normalizedValues());
        metadata.put("docSemanticType", artifact.classification().effectiveSemanticType().name());
        put(metadata, "sourceRef", evidence.sourceRef());
        put(metadata, "page", evidence.page());
        put(metadata, "slide", evidence.slide());
        put(metadata, "section", evidence.section());
        put(metadata, "startOffset", evidence.startOffset());
        put(metadata, "endOffset", evidence.endOffset());
        if (evidence.blockId() != null) {
            metadata.put("blockIds", List.of(evidence.blockId()));
        }
        DocumentMetadataField title = artifact.field("title");
        if (title != null && !title.normalizedValues().isEmpty()) {
            metadata.put("docTitle", title.normalizedValues().get(0));
        }
        return new RagSearchResult(
                current.documentId() + ":metadata:" + fieldId,
                evidence.exactText(),
                Map.copyOf(metadata),
                field.confidence());
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
            target.put(key, value);
        }
    }

    private record CurrentArtifact(String documentId, String revisionId, String metadataJson) {
    }
}
