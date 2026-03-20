package studio.one.application.mail.domain.entity;

import java.time.Instant;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Data;
import lombok.NoArgsConstructor;
import studio.one.application.mail.domain.model.MailMessage;

@Data
@NoArgsConstructor
@Entity
@Table(name = "TB_APPLICATION_MAIL_MESSAGE")
@EntityListeners(AuditingEntityListener.class)
public class MailMessageEntity implements MailMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MAIL_ID", nullable = false)
    private long mailId;

    @Column(name = "FOLDER", nullable = false)
    private String folder;

    @Column(name = "UID", nullable = false)
    private long uid;

    @Column(name = "MESSAGE_ID")
    private String messageId;

    @Column(name = "SUBJECT")
    private String subject;

    @Column(name = "FROM_ADDRESS")
    private String fromAddress;

    @Column(name = "TO_ADDRESS")
    private String toAddress;

    @Column(name = "CC_ADDRESS")
    private String ccAddress;

    @Column(name = "BCC_ADDRESS")
    private String bccAddress;

    @Column(name = "SENT_AT")
    private Instant sentAt;

    @Column(name = "RECEIVED_AT")
    private Instant receivedAt;

    @Column(name = "FLAGS")
    private String flags;

    @Column(name = "BODY")
    private String body;

    @CreatedDate
    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "TB_APPLICATION_MAIL_PROPERTY", joinColumns = {
            @JoinColumn(name = "MAIL_ID", referencedColumnName = "MAIL_ID") })
    @MapKeyColumn(name = "PROPERTY_NAME")
    @Column(name = "PROPERTY_VALUE")
    private Map<String, String> properties;
}
