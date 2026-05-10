package studio.one.application.attachment.domain.model;

public record AttachmentDownloadAuditLogCount(
        Long issueLogId,
        String tokenHash,
        long count) {
}
