package studio.one.application.attachment.persistence;

import studio.one.application.attachment.domain.entity.AttachmentDownloadUrlIssueAuditLog;

public interface AttachmentDownloadUrlIssueAuditLogRepository {

    AttachmentDownloadUrlIssueAuditLog save(AttachmentDownloadUrlIssueAuditLog log);
}
