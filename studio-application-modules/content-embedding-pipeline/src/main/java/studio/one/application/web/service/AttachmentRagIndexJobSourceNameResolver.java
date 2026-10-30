package studio.one.application.web.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;
import studio.one.platform.ai.service.pipeline.RagIndexJobSourceNameResolver;

@Component
@RequiredArgsConstructor
public class AttachmentRagIndexJobSourceNameResolver implements RagIndexJobSourceNameResolver {

    private static final String ATTACHMENT = "attachment";

    private final AttachmentService attachmentService;

    @Override
    public boolean supports(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest) {
        return request != null && request.indexRequest() == null && ATTACHMENT.equalsIgnoreCase(request.sourceType());
    }

    @Override
    public Optional<String> resolveSourceName(
            RagIndexJobCreateRequest request,
            RagIndexJobSourceRequest sourceRequest) {
        return attachmentId(request, sourceRequest)
                .flatMap(this::attachmentName);
    }

    private Optional<Long> attachmentId(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest) {
        if (!ATTACHMENT.equalsIgnoreCase(text(request.objectType()).orElse(null))) {
            return Optional.empty();
        }
        Optional<Long> objectAttachmentId = parseAttachmentId(request.objectId());
        if (objectAttachmentId.isEmpty()) {
            return Optional.empty();
        }
        if (sourceRequest == null || !sourceRequest.metadata().containsKey("attachmentId")) {
            return objectAttachmentId;
        }
        Object metadataAttachmentId = sourceRequest.metadata().get("attachmentId");
        if (metadataAttachmentId == null) {
            return objectAttachmentId;
        }
        Optional<String> metadataAttachmentIdText = text(metadataAttachmentId.toString());
        if (metadataAttachmentIdText.isEmpty()) {
            return Optional.empty();
        }
        Optional<Long> sourceAttachmentId = parseAttachmentId(metadataAttachmentIdText.get());
        if (sourceAttachmentId.isEmpty() || !sourceAttachmentId.equals(objectAttachmentId)) {
            return Optional.empty();
        }
        return sourceAttachmentId;
    }

    private Optional<Long> parseAttachmentId(String value) {
        try {
            long attachmentId = Long.parseLong(value.trim());
            return attachmentId <= 0L ? Optional.empty() : Optional.of(attachmentId);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<String> attachmentName(long attachmentId) {
        try {
            Attachment attachment = attachmentService.getAttachmentById(attachmentId);
            return text(attachment == null ? null : attachment.getName());
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<String> text(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }
}
