package studio.one.application.attachment.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.application.attachment.domain.entity.AttachmentDownloadAuditLog;
import studio.one.application.attachment.domain.entity.AttachmentDownloadUrlIssueAuditLog;

public interface AttachmentDownloadAuditLogService {

    AttachmentDownloadAuditLog record(AttachmentDownloadAuditLogCommand command);

    Page<AttachmentDownloadAuditLog> find(AttachmentDownloadAuditLogQuery query, Pageable pageable);

    Map<Long, Long> countByIssueLogs(List<AttachmentDownloadUrlIssueAuditLog> issueLogs);
}
