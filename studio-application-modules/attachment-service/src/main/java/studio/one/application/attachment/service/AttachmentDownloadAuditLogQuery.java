package studio.one.application.attachment.service;

import java.time.Instant;

public record AttachmentDownloadAuditLogQuery(
        Long attachmentId,
        Integer objectType,
        Long objectId,
        String tokenHash,
        AttachmentDownloadAuditResult result,
        Instant from,
        Instant to,
        String clientIp) {
}
