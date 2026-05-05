package studio.one.application.attachment.service;

import java.time.Instant;

public record AttachmentDownloadAuditLogCommand(
        String tokenHash,
        Long attachmentId,
        Integer objectType,
        Long objectId,
        String linkType,
        Instant requestedAt,
        AttachmentDownloadAuditResult result,
        int httpStatus,
        Long downloadedBytes,
        String clientIp,
        String userAgent,
        String errorCode) {
}
