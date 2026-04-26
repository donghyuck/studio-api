package studio.one.application.web.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.platform.ai.core.rag.RagIndexJobLogCode;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.service.pipeline.RagIndexProgressListener;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.exception.NotFoundException;
import studio.one.platform.textract.service.FileContentExtractionService;

@Service
@RequiredArgsConstructor
public class AttachmentRagIndexService {

    private final AttachmentService attachmentService;
    private final ObjectProvider<FileContentExtractionService> textExtractionProvider;
    private final ObjectProvider<RagPipelineService> ragPipelineProvider;
    private final ObjectProvider<AttachmentStructuredRagIndexer> structuredRagIndexerProvider;

    public AttachmentRagIndexCommand command(long attachmentId,
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            List<String> keywords,
            Boolean useLlmKeywordExtraction) {
        return new AttachmentRagIndexCommand(
                hasText(documentId) ? documentId.trim() : String.valueOf(attachmentId),
                hasText(objectType) ? objectType.trim() : "attachment",
                hasText(objectId) ? objectId.trim() : String.valueOf(attachmentId),
                metadata,
                keywords,
                Boolean.TRUE.equals(useLlmKeywordExtraction));
    }

    public boolean hasTextExtractor() {
        return textExtractionProvider.getIfAvailable() != null;
    }

    public AttachmentRagIndexResult index(
            long attachmentId,
            AttachmentRagIndexCommand command,
            RagIndexProgressListener listener) throws NotFoundException, IOException {
        FileContentExtractionService extractor = textExtractionProvider.getIfAvailable();
        if (extractor == null) {
            throw new AttachmentRagIndexUnavailableException("Text extraction is not configured");
        }
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        RagIndexProgressListener progress = listener == null ? RagIndexProgressListener.noop() : listener;
        Map<String, Object> metadata = buildMetadata(command, attachment);
        AttachmentStructuredRagIndexer structuredIndexer = structuredRagIndexerProvider.getIfAvailable();
        AttachmentRagIndexDiagnostics structuredDiagnostics = structuredIndexer == null
                ? AttachmentRagIndexDiagnostics.fallback("missing_structured_indexer")
                : null;
        try {
            if (structuredIndexer != null) {
                try (InputStream in = attachmentService.getInputStream(attachment)) {
                    if (structuredIndexer.index(
                            attachment,
                            command.documentId(),
                            command.objectType(),
                            command.objectId(),
                            metadata,
                            extractor,
                            in,
                            progress)) {
                        return new AttachmentRagIndexResult(structuredIndexer.latestDiagnostics()
                                .orElse(AttachmentRagIndexDiagnostics.structuredUnknown()));
                    }
                    structuredDiagnostics = structuredIndexer.latestDiagnostics()
                            .orElse(AttachmentRagIndexDiagnostics.fallback("structured_not_handled"));
                } finally {
                    structuredIndexer.clearDiagnostics();
                }
            }
            RagPipelineService ragPipeline = ragPipelineProvider.getIfAvailable();
            if (ragPipeline == null) {
                progress.onError(null, RagIndexJobLogCode.SOURCE_UNSUPPORTED,
                        "RAG pipeline service is not configured", null);
                throw new AttachmentRagIndexUnavailableException("RAG pipeline service is not configured");
            }
            try (InputStream in = attachmentService.getInputStream(attachment)) {
                String text = extractor.extractText(attachment.getContentType(), attachment.getName(), in);
                ragPipeline.index(new RagIndexRequest(
                        command.documentId(),
                        text,
                        metadata,
                        command.keywords(),
                        command.useLlmKeywordExtraction()), progress);
            }
            return new AttachmentRagIndexResult(AttachmentRagIndexDiagnostics.fallback(
                    structuredDiagnostics == null ? "structured_not_attempted" : structuredDiagnostics.fallbackReason()));
        } catch (AttachmentRagIndexUnavailableException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            progress.onError(null, RagIndexJobLogCode.UNKNOWN_ERROR, "Attachment RAG index failed", ex.getMessage());
            throw ex;
        }
    }

    private Map<String, Object> buildMetadata(AttachmentRagIndexCommand command, Attachment attachment) {
        Map<String, Object> metadata = new HashMap<>(command.metadata());
        metadata.putIfAbsent("objectType", command.objectType());
        metadata.putIfAbsent("objectId", command.objectId());
        metadata.putIfAbsent("attachmentId", attachment.getAttachmentId());
        metadata.putIfAbsent("name", attachment.getName());
        metadata.putIfAbsent("filename", attachment.getName());
        metadata.putIfAbsent("sourceType", "attachment");
        metadata.putIfAbsent("indexedAt", Instant.now().toString());
        metadata.putIfAbsent("contentType", attachment.getContentType());
        metadata.putIfAbsent("size", attachment.getSize());
        metadata.putIfAbsent("chunkOrder", 0);
        return metadata;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
