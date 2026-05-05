package studio.one.application.attachment.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.application.attachment.domain.entity.AttachmentDownloadUrlIssueAuditLog;

public interface AttachmentDownloadUrlIssueAuditLogQueryService {

    Page<AttachmentDownloadUrlIssueAuditLog> find(AttachmentDownloadUrlIssueAuditLogQuery query, Pageable pageable);
}
