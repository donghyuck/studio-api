package studio.one.application.attachment.application.command;

import studio.one.application.attachment.application.result.*;

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
