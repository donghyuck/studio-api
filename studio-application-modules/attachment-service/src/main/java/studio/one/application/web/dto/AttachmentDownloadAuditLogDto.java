package studio.one.application.web.dto;

import java.time.Instant;

import studio.one.application.attachment.domain.entity.AttachmentDownloadAuditLog;

public record AttachmentDownloadAuditLogDto(
        Long downloadLogId,
        Long issueLogId,
        String tokenHash,
        Long attachmentId,
        Integer objectType,
        Long objectId,
        String linkType,
        Instant requestedAt,
        String result,
        Integer httpStatus,
        Long downloadedBytes,
        String clientIp,
        String userAgent,
        String errorCode) {

    public static AttachmentDownloadAuditLogDto from(AttachmentDownloadAuditLog log) {
        return new AttachmentDownloadAuditLogDto(
                log.getDownloadLogId(),
                log.getIssueLogId(),
                log.getTokenHash(),
                log.getAttachmentId(),
                log.getObjectType(),
                log.getObjectId(),
                log.getLinkType(),
                log.getRequestedAt(),
                log.getResult(),
                log.getHttpStatus(),
                log.getDownloadedBytes(),
                log.getClientIp(),
                log.getUserAgent(),
                log.getErrorCode());
    }
}
