package studio.one.application.attachment.domain.port;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.application.attachment.application.command.AttachmentDownloadAuditLogQuery;
import studio.one.application.attachment.domain.model.AttachmentDownloadAuditLog;
import studio.one.application.attachment.domain.model.AttachmentDownloadAuditLogCount;

public interface AttachmentDownloadAuditLogRepository {

    AttachmentDownloadAuditLog save(AttachmentDownloadAuditLog log);

    Page<AttachmentDownloadAuditLog> search(AttachmentDownloadAuditLogQuery query, Pageable pageable);

    List<AttachmentDownloadAuditLogCount> countByIssueLogIdsOrTokenHashes(
            Collection<Long> issueLogIds,
            Collection<String> tokenHashes);
}
