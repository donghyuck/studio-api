package studio.one.application.attachment.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.application.attachment.domain.entity.AttachmentDownloadAuditLog;
import studio.one.application.attachment.service.AttachmentDownloadAuditLogQuery;

public interface AttachmentDownloadAuditLogRepository {

    AttachmentDownloadAuditLog save(AttachmentDownloadAuditLog log);

    Page<AttachmentDownloadAuditLog> search(AttachmentDownloadAuditLogQuery query, Pageable pageable);
}
