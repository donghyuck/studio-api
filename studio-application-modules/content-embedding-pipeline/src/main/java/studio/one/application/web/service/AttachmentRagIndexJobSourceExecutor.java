package studio.one.application.web.service;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;
import studio.one.platform.ai.service.pipeline.RagIndexJobSourceExecutor;
import studio.one.platform.ai.service.pipeline.RagIndexProgressListener;

@Component
@RequiredArgsConstructor
public class AttachmentRagIndexJobSourceExecutor implements RagIndexJobSourceExecutor {

    private static final String ATTACHMENT = "attachment";

    private final AttachmentRagIndexService ragIndexService;

    @Override
    public boolean supports(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest) {
        if (request == null || request.indexRequest() != null) {
            return false;
        }
        return ATTACHMENT.equalsIgnoreCase(request.sourceType());
    }

    @Override
    public void execute(
            RagIndexJob job,
            RagIndexJobCreateRequest request,
            RagIndexJobSourceRequest sourceRequest,
            RagIndexProgressListener listener) {
        RagIndexJobSourceRequest source = sourceRequest == null
                ? RagIndexJobSourceRequest.empty()
                : sourceRequest;
        long attachmentId = attachmentId(request, source);
        AttachmentRagIndexCommand command = ragIndexService.command(
                attachmentId,
                request.documentId(),
                request.objectType(),
                request.objectId(),
                source.metadata(),
                source.keywords(),
                source.useLlmKeywordExtraction());
        try {
            ragIndexService.index(attachmentId, command, listener);
        } catch (Exception ex) {
            throw new IllegalStateException("Attachment RAG index job failed", ex);
        }
    }

    private long attachmentId(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest) {
        Object metadataAttachmentId = sourceRequest.metadata().get("attachmentId");
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
