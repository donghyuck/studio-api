package studio.one.application.attachment.application.usecase;

import studio.one.application.attachment.application.command.*;
import studio.one.application.attachment.application.result.*;
import studio.one.application.attachment.application.service.AttachmentDownloadTokenCodec;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.application.attachment.infrastructure.persistence.model.AttachmentDownloadUrlIssueAuditLog;

public interface AttachmentDownloadUrlIssueAuditLogQueryService {

    Page<AttachmentDownloadUrlIssueAuditLog> find(AttachmentDownloadUrlIssueAuditLogQuery query, Pageable pageable);
}
