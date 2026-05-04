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
@Table(name = "TB_APPLICATION_ATTACHMENT_URL_ISSUE_LOG")
public class AttachmentDownloadUrlIssueAuditLog {

    @Id
    @Column(name = "LOG_ID", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    @Column(name = "ATTACHMENT_ID", nullable = false)
    private Long attachmentId;

    @Column(name = "OBJECT_TYPE", nullable = false)
    private Integer objectType;

    @Column(name = "OBJECT_ID", nullable = false)
    private Long objectId;

    @Column(name = "ENDPOINT_KIND", nullable = false, length = 20)
    private String endpointKind;

    @Column(name = "ISSUED_BY_USER_ID")
    private Long issuedByUserId;

    @Column(name = "ISSUED_BY_PRINCIPAL_NAME", length = 255)
    private String issuedByPrincipalName;

    @Column(name = "ISSUED_AT", nullable = false)
    private Instant issuedAt;

    @Column(name = "EXPIRES_AT", nullable = false)
    private Instant expiresAt;

    @Column(name = "TTL_SECONDS", nullable = false)
    private Long ttlSeconds;

    @Column(name = "STORAGE_PROVIDER_ID", nullable = false, length = 100)
    private String storageProviderId;

    @Column(name = "BUCKET", nullable = false, length = 255)
    private String bucket;

    @Column(name = "OBJECT_KEY_HASH", nullable = false, length = 64)
    private String objectKeyHash;

    @Column(name = "CLIENT_IP", length = 45)
    private String clientIp;

    @Column(name = "USER_AGENT", length = 512)
    private String userAgent;
}
