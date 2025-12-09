package studio.one.application.mail.domain.model;

import java.time.Instant;

public interface MailAttachment {

    long getAttachmentId();
    void setAttachmentId(long id);

    long getMailId();
    void setMailId(long mailId);

    String getFilename();
    void setFilename(String filename);

    String getContentType();
    void setContentType(String contentType);

    long getSize();
    void setSize(long size);

    byte[] getContent();
    void setContent(byte[] content);

    Instant getCreatedAt();
    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();
    void setUpdatedAt(Instant updatedAt);
}
