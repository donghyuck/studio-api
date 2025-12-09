package studio.one.application.mail.domain.model;

import java.time.Instant;
import java.util.Map;

import studio.one.platform.domain.model.PropertyAware;

public interface MailMessage extends PropertyAware {

    long getMailId();
    void setMailId(long mailId);

    String getFolder();
    void setFolder(String folder);

    long getUid();
    void setUid(long uid);

    String getMessageId();
    void setMessageId(String messageId);

    String getSubject();
    void setSubject(String subject);

    String getFromAddress();
    void setFromAddress(String fromAddress);

    String getToAddress();
    void setToAddress(String toAddress);

    String getCcAddress();
    void setCcAddress(String ccAddress);

    String getBccAddress();
    void setBccAddress(String bccAddress);

    Instant getSentAt();
    void setSentAt(Instant sentAt);

    Instant getReceivedAt();
    void setReceivedAt(Instant receivedAt);

    String getFlags();
    void setFlags(String flags);

    String getBody();
    void setBody(String body);

    Instant getCreatedAt();
    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();
    void setUpdatedAt(Instant updatedAt);

    @Override
    Map<String, String> getProperties();

    @Override
    void setProperties(Map<String, String> properties);
}
