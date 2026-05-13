package studio.one.application.attachment.web.dto.response;

import java.time.Instant;

import studio.one.application.attachment.domain.model.AttachmentDownloadUrlIssueAuditLog;

public class AttachmentDownloadUrlIssueAuditLogDto {

    private final Long logId;
    private final Long attachmentId;
    private final Integer objectType;
    private final Long objectId;
    private final String endpointKind;
    private final Long issuedByUserId;
    private final String issuedByPrincipalName;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Long ttlSeconds;
    private final String linkType;
    private final String tokenHash;
    private final Long downloadCount;
    private final String storageProviderId;
    private final String bucket;
    private final String objectKeyHash;
    private final String clientIp;
    private final String userAgent;

    public AttachmentDownloadUrlIssueAuditLogDto(
            Long logId,
            Long attachmentId,
            Integer objectType,
            Long objectId,
            String endpointKind,
            Long issuedByUserId,
            String issuedByPrincipalName,
            Instant issuedAt,
            Instant expiresAt,
            Long ttlSeconds,
            String linkType,
            String tokenHash,
            Long downloadCount,
            String storageProviderId,
            String bucket,
            String objectKeyHash,
            String clientIp,
            String userAgent) {
        this.logId = logId;
        this.attachmentId = attachmentId;
        this.objectType = objectType;
        this.objectId = objectId;
        this.endpointKind = endpointKind;
        this.issuedByUserId = issuedByUserId;
        this.issuedByPrincipalName = issuedByPrincipalName;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.ttlSeconds = ttlSeconds;
        this.linkType = linkType;
        this.tokenHash = tokenHash;
        this.downloadCount = downloadCount;
        this.storageProviderId = storageProviderId;
        this.bucket = bucket;
        this.objectKeyHash = objectKeyHash;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
    }

    public Long logId() { return logId; }

    public Long attachmentId() { return attachmentId; }

    public Integer objectType() { return objectType; }

    public Long objectId() { return objectId; }

    public String endpointKind() { return endpointKind; }

    public Long issuedByUserId() { return issuedByUserId; }

    public String issuedByPrincipalName() { return issuedByPrincipalName; }

    public Instant issuedAt() { return issuedAt; }

    public Instant expiresAt() { return expiresAt; }

    public Long ttlSeconds() { return ttlSeconds; }

    public String linkType() { return linkType; }

    public String tokenHash() { return tokenHash; }

    public Long downloadCount() { return downloadCount; }

    public String storageProviderId() { return storageProviderId; }

    public String bucket() { return bucket; }

    public String objectKeyHash() { return objectKeyHash; }

    public String clientIp() { return clientIp; }

    public String userAgent() { return userAgent; }

public static AttachmentDownloadUrlIssueAuditLogDto from(AttachmentDownloadUrlIssueAuditLog log) {
        return from(log, 0L);
    }

    public static AttachmentDownloadUrlIssueAuditLogDto from(
            AttachmentDownloadUrlIssueAuditLog log,
            long downloadCount) {
        return new AttachmentDownloadUrlIssueAuditLogDto(
                log.getLogId(),
                log.getAttachmentId(),
                log.getObjectType(),
                log.getObjectId(),
                log.getEndpointKind(),
                log.getIssuedByUserId(),
                log.getIssuedByPrincipalName(),
                log.getIssuedAt(),
                log.getExpiresAt(),
                log.getTtlSeconds(),
                log.getLinkType(),
                log.getTokenHash(),
                downloadCount,
                log.getStorageProviderId(),
                log.getBucket(),
                log.getObjectKeyHash(),
                log.getClientIp(),
                log.getUserAgent());
    }
    public Long getLogId() { return logId; }

    public Long getAttachmentId() { return attachmentId; }

    public Integer getObjectType() { return objectType; }

    public Long getObjectId() { return objectId; }

    public String getEndpointKind() { return endpointKind; }

    public Long getIssuedByUserId() { return issuedByUserId; }

    public String getIssuedByPrincipalName() { return issuedByPrincipalName; }

    public Instant getIssuedAt() { return issuedAt; }

    public Instant getExpiresAt() { return expiresAt; }

    public Long getTtlSeconds() { return ttlSeconds; }

    public String getLinkType() { return linkType; }

    public String getTokenHash() { return tokenHash; }

    public Long getDownloadCount() { return downloadCount; }

    public String getStorageProviderId() { return storageProviderId; }

    public String getBucket() { return bucket; }

    public String getObjectKeyHash() { return objectKeyHash; }

    public String getClientIp() { return clientIp; }

    public String getUserAgent() { return userAgent; }

}
