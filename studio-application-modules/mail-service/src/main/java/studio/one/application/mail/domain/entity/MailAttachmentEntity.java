package studio.one.application.mail.domain.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Data;
import lombok.NoArgsConstructor;
import studio.one.application.mail.domain.model.MailAttachment;

@Data
@NoArgsConstructor
@Entity
@Table(name = "TB_APPLICATION_MAIL_ATTACHMENT")
@EntityListeners(AuditingEntityListener.class)
public class MailAttachmentEntity implements MailAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ATTACHMENT_ID", nullable = false)
    private long attachmentId;

    @Column(name = "MAIL_ID", nullable = false)
    private long mailId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MAIL_ID", insertable = false, updatable = false)
    private MailMessageEntity mailMessage;

    @Column(name = "FILENAME")
    private String filename;

    @Column(name = "CONTENT_TYPE")
    private String contentType;

    @Column(name = "SIZE")
    private long size;

    @Lob
    @Column(name = "CONTENT")
    private byte[] content;

    @CreatedDate
    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "UPDATED_AT")
    private Instant updatedAt;
}
