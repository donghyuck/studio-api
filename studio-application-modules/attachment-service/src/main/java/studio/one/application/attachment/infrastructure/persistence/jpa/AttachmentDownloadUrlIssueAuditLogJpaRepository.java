package studio.one.application.attachment.infrastructure.persistence.jpa;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import studio.one.application.attachment.domain.model.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.domain.port.AttachmentDownloadUrlIssueAuditLogRepository;
import studio.one.application.attachment.application.command.AttachmentDownloadUrlIssueAuditLogQuery;

public interface AttachmentDownloadUrlIssueAuditLogJpaRepository
        extends JpaRepository<AttachmentDownloadUrlIssueAuditLog, Long>,
        JpaSpecificationExecutor<AttachmentDownloadUrlIssueAuditLog>,
        AttachmentDownloadUrlIssueAuditLogRepository {

    Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("issuedAt"), Sort.Order.desc("logId"));

    @Override
    default Page<AttachmentDownloadUrlIssueAuditLog> search(
            AttachmentDownloadUrlIssueAuditLogQuery query,
            Pageable pageable) {
        Specification<AttachmentDownloadUrlIssueAuditLog> spec =
                AttachmentDownloadUrlIssueAuditLogSpecifications.from(query);
        if (pageable == null || pageable.isUnpaged()) {
            List<AttachmentDownloadUrlIssueAuditLog> content = findAll(spec, DEFAULT_SORT);
            return new PageImpl<>(content);
        }
        Pageable resolved = pageable.getSort().isUnsorted()
                ? org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        DEFAULT_SORT)
                : pageable;
        return findAll(spec, resolved);
    }
}
