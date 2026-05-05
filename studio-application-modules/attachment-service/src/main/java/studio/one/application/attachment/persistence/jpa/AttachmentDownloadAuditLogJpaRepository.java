package studio.one.application.attachment.persistence.jpa;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import studio.one.application.attachment.domain.entity.AttachmentDownloadAuditLog;
import studio.one.application.attachment.persistence.AttachmentDownloadAuditLogCount;
import studio.one.application.attachment.persistence.AttachmentDownloadAuditLogRepository;
import studio.one.application.attachment.service.AttachmentDownloadAuditLogQuery;

public interface AttachmentDownloadAuditLogJpaRepository
        extends JpaRepository<AttachmentDownloadAuditLog, Long>,
        JpaSpecificationExecutor<AttachmentDownloadAuditLog>,
        AttachmentDownloadAuditLogRepository {

    Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("downloadLogId"));

    @Override
    @Query("""
            select new studio.one.application.attachment.persistence.AttachmentDownloadAuditLogCount(
                log.issueLogId,
                log.tokenHash,
                count(log)
            )
            from AttachmentDownloadAuditLog log
            where log.issueLogId in :issueLogIds
               or log.tokenHash in :tokenHashes
            group by log.issueLogId, log.tokenHash
            """)
    List<AttachmentDownloadAuditLogCount> countByIssueLogIdsOrTokenHashes(
            @Param("issueLogIds") Collection<Long> issueLogIds,
            @Param("tokenHashes") Collection<String> tokenHashes);

    @Override
    default Page<AttachmentDownloadAuditLog> search(AttachmentDownloadAuditLogQuery query, Pageable pageable) {
        Specification<AttachmentDownloadAuditLog> spec = AttachmentDownloadAuditLogSpecifications.from(query);
        if (pageable == null || pageable.isUnpaged()) {
            List<AttachmentDownloadAuditLog> content = findAll(spec, DEFAULT_SORT);
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
