package studio.one.application.attachment.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.application.attachment.domain.entity.AttachmentDownloadAuditLog;

public interface AttachmentDownloadAuditLogService {

    AttachmentDownloadAuditLog record(AttachmentDownloadAuditLogCommand command);

    Page<AttachmentDownloadAuditLog> find(AttachmentDownloadAuditLogQuery query, Pageable pageable);
}
