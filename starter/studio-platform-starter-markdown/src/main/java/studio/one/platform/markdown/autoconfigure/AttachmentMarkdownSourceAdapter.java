package studio.one.platform.markdown.autoconfigure;

import java.io.IOException;
import java.io.InputStream;

import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.markdown.application.MarkdownSourceTooLargeException;
import studio.one.platform.markdown.application.port.MarkdownSourcePort;

public class AttachmentMarkdownSourceAdapter implements MarkdownSourcePort {
    private final AttachmentService attachmentService;
    private final int maxSourceBytes;

    public AttachmentMarkdownSourceAdapter(AttachmentService attachmentService, int maxSourceBytes) {
        this.attachmentService = attachmentService;
        this.maxSourceBytes = Math.max(1, maxSourceBytes);
    }

    @Override
    public MarkdownSourceDescriptor describe(long attachmentId) {
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        return new MarkdownSourceDescriptor(attachment.getAttachmentId(), attachment.getName(),
                attachment.getContentType(), String.valueOf(attachment.getObjectType()),
                String.valueOf(attachment.getObjectId()), attachment.getSize());
    }

    @Override
    public MarkdownSource load(long attachmentId) {
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        if (attachment.getSize() > maxSourceBytes) {
            throw new MarkdownSourceTooLargeException(attachment.getSize(), maxSourceBytes);
        }
        try (InputStream input = attachmentService.getInputStream(attachment)) {
            byte[] content = input.readNBytes(maxSourceBytes + 1);
            if (content.length > maxSourceBytes) {
                throw new MarkdownSourceTooLargeException(content.length, maxSourceBytes);
            }
            return new MarkdownSource(attachment.getAttachmentId(), attachment.getName(),
                    attachment.getContentType(), String.valueOf(attachment.getObjectType()),
                    String.valueOf(attachment.getObjectId()), content);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read attachment", ex);
        }
    }
}
