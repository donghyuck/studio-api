package studio.one.application.attachment.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.application.attachment.domain.entity.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.persistence.AttachmentDownloadUrlIssueAuditLogRepository;

public interface AttachmentDownloadUrlIssueAuditLogJpaRepository
        extends JpaRepository<AttachmentDownloadUrlIssueAuditLog, Long>, AttachmentDownloadUrlIssueAuditLogRepository {
}
