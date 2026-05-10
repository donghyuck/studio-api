package studio.one.application.attachment.application.command;

import studio.one.application.attachment.application.result.*;

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
