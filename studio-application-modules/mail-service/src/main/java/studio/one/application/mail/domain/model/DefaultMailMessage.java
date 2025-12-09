package studio.one.application.mail.domain.model;

import java.time.Instant;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DefaultMailMessage implements MailMessage {

    private long mailId;
    private String folder;
    private long uid;
    private String messageId;
    private String subject;
    private String fromAddress;
    private String toAddress;
    private String ccAddress;
    private String bccAddress;
    private Instant sentAt;
    private Instant receivedAt;
    private String flags;
    private String body;
    private Instant createdAt;
    private Instant updatedAt;
    private Map<String, String> properties;
    
}
