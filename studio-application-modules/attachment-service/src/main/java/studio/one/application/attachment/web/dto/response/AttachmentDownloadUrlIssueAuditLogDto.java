package studio.one.application.attachment.web.dto.response;

import java.time.Instant;

import studio.one.application.attachment.domain.model.AttachmentDownloadUrlIssueAuditLog;

public record AttachmentDownloadUrlIssueAuditLogDto(
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
}
