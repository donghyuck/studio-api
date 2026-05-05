package studio.one.application.attachment.domain.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "TB_APPLICATION_ATTACHMENT_DOWNLOAD_LOG")
public class AttachmentDownloadAuditLog {

    @Id
    @Column(name = "DOWNLOAD_LOG_ID", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long downloadLogId;

    @Column(name = "ISSUE_LOG_ID")
    private Long issueLogId;

    @Column(name = "TOKEN_HASH", length = 64)
    private String tokenHash;

    @Column(name = "ATTACHMENT_ID")
    private Long attachmentId;

    @Column(name = "OBJECT_TYPE")
    private Integer objectType;

    @Column(name = "OBJECT_ID")
    private Long objectId;

    @Column(name = "LINK_TYPE", nullable = false, length = 40)
    private String linkType;

    @Column(name = "REQUESTED_AT", nullable = false)
    private Instant requestedAt;

    @Column(name = "RESULT", nullable = false, length = 40)
    private String result;

    @Column(name = "HTTP_STATUS", nullable = false)
    private Integer httpStatus;

    @Column(name = "DOWNLOADED_BYTES")
    private Long downloadedBytes;

    @Column(name = "CLIENT_IP", length = 45)
    private String clientIp;

    @Column(name = "USER_AGENT", length = 512)
    private String userAgent;

    @Column(name = "ERROR_CODE", length = 80)
    private String errorCode;
}
