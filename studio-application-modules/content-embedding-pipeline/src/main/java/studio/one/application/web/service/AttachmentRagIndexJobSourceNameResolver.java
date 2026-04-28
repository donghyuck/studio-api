package studio.one.application.web.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
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
        Object metadataAttachmentId = sourceRequest == null ? null : sourceRequest.metadata().get("attachmentId");
        String value = metadataAttachmentId == null ? request.objectId() : metadataAttachmentId.toString();
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            long attachmentId = Long.parseLong(value.trim());
            return attachmentId <= 0L ? Optional.empty() : Optional.of(attachmentId);
        } catch (NumberFormatException ex) {
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
