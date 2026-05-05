package studio.one.application.attachment.persistence;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.application.attachment.domain.entity.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.service.AttachmentDownloadUrlIssueAuditLogQuery;

public interface AttachmentDownloadUrlIssueAuditLogRepository {

    AttachmentDownloadUrlIssueAuditLog save(AttachmentDownloadUrlIssueAuditLog log);

    Optional<AttachmentDownloadUrlIssueAuditLog> findByTokenHash(String tokenHash);

    Page<AttachmentDownloadUrlIssueAuditLog> search(
            AttachmentDownloadUrlIssueAuditLogQuery query,
            Pageable pageable);
}
