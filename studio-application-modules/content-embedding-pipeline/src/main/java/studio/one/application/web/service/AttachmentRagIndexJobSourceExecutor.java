package studio.one.application.web.service;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.service.pipeline.RagIndexJobSourceExecutor;
import studio.one.platform.ai.service.pipeline.RagIndexProgressListener;

@Component
@RequiredArgsConstructor
public class AttachmentRagIndexJobSourceExecutor implements RagIndexJobSourceExecutor {

    private static final String ATTACHMENT = "attachment";

    private final AttachmentRagIndexService ragIndexService;

    @Override
    public boolean supports(RagIndexJobCreateRequest request) {
        if (request == null || request.indexRequest() != null) {
            return false;
        }
        return ATTACHMENT.equalsIgnoreCase(request.sourceType())
                || ATTACHMENT.equalsIgnoreCase(request.objectType());
    }

    @Override
    public void execute(RagIndexJob job, RagIndexJobCreateRequest request, RagIndexProgressListener listener) {
        long attachmentId = attachmentId(request);
        AttachmentRagIndexCommand command = ragIndexService.command(
                attachmentId,
                request.documentId(),
                request.objectType(),
                request.objectId(),
                request.metadata(),
                request.keywords(),
                request.useLlmKeywordExtraction());
        try {
            ragIndexService.index(attachmentId, command, listener);
        } catch (Exception ex) {
            throw new IllegalStateException("Attachment RAG index job failed", ex);
        }
    }

    private long attachmentId(RagIndexJobCreateRequest request) {
        Object metadataAttachmentId = request.metadata().get("attachmentId");
        String value = metadataAttachmentId == null ? request.objectId() : metadataAttachmentId.toString();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("attachmentId is required for attachment RAG index job");
        }
        try {
            long attachmentId = Long.parseLong(value.trim());
            if (attachmentId <= 0L) {
                throw new IllegalArgumentException("attachmentId must be positive");
            }
            return attachmentId;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("attachmentId must be numeric", ex);
        }
    }
}
