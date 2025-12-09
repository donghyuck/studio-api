package studio.one.application.mail.domain.model;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DefaultMailAttachment implements MailAttachment {
    private long attachmentId;
    private long mailId;
    private String filename;
    private String contentType;
    private long size;
    private byte[] content;
    private Instant createdAt;
    private Instant updatedAt;
}
