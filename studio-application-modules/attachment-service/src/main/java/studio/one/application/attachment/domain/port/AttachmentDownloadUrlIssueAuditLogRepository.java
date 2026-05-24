package studio.one.application.attachment.domain.port;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.application.attachment.application.command.AttachmentDownloadUrlIssueAuditLogQuery;
import studio.one.application.attachment.domain.model.AttachmentDownloadUrlIssueAuditLog;

public interface AttachmentDownloadUrlIssueAuditLogRepository {

    AttachmentDownloadUrlIssueAuditLog save(AttachmentDownloadUrlIssueAuditLog log);

    Optional<AttachmentDownloadUrlIssueAuditLog> findByTokenHash(String tokenHash);

    Page<AttachmentDownloadUrlIssueAuditLog> search(
            AttachmentDownloadUrlIssueAuditLogQuery query,
            Pageable pageable);
}
