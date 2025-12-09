package studio.one.application.mail.web.dto;

import lombok.Builder;
import lombok.Value;
import studio.one.application.mail.domain.model.MailAttachment;

@Value
@Builder
public class MailAttachmentDto {
    long attachmentId;
    String filename;
    String contentType;
    long size;

    public static MailAttachmentDto from(MailAttachment attachment) {
        return MailAttachmentDto.builder()
                .attachmentId(attachment.getAttachmentId())
                .filename(attachment.getFilename())
                .contentType(attachment.getContentType())
                .size(attachment.getSize())
                .build();
    }
}
