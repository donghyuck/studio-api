package studio.one.application.attachment.application.usecase;

import studio.one.application.attachment.application.command.*;
import studio.one.application.attachment.application.result.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.application.attachment.domain.model.AttachmentDownloadUrlIssueAuditLog;

public interface AttachmentDownloadUrlIssueAuditLogQueryService {

    Page<AttachmentDownloadUrlIssueAuditLog> find(AttachmentDownloadUrlIssueAuditLogQuery query, Pageable pageable);
}
