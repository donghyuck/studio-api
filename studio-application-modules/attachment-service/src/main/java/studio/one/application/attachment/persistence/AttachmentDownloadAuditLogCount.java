package studio.one.application.attachment.persistence;

public record AttachmentDownloadAuditLogCount(
        Long issueLogId,
        String tokenHash,
        long count) {
}
