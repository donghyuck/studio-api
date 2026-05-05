package studio.one.application.attachment.service;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import studio.one.application.attachment.domain.entity.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.persistence.AttachmentDownloadUrlIssueAuditLogRepository;

@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentDownloadUrlIssueAuditLogQueryServiceImpl implements AttachmentDownloadUrlIssueAuditLogQueryService {

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("issuedAt"),
            Sort.Order.desc("logId"));

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "logId",
            "attachmentId",
            "objectType",
            "objectId",
            "endpointKind",
            "issuedByUserId",
            "issuedByPrincipalName",
            "issuedAt",
            "expiresAt",
            "ttlSeconds",
            "storageProviderId",
            "bucket",
            "objectKeyHash",
            "clientIp",
            "userAgent");

    private final AttachmentDownloadUrlIssueAuditLogRepository repository;

    @Override
    public Page<AttachmentDownloadUrlIssueAuditLog> find(
            AttachmentDownloadUrlIssueAuditLogQuery query,
            Pageable pageable) {
        return repository.search(query, normalize(pageable));
    }

    private Pageable normalize(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return pageable;
        }
        Sort sort = sanitize(pageable.getSort());
        if (sort.isUnsorted()) {
            sort = DEFAULT_SORT;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private Sort sanitize(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return Sort.unsorted();
        }
        List<Sort.Order> orders = sort.stream()
                .filter(order -> ALLOWED_SORT_PROPERTIES.contains(order.getProperty()))
                .toList();
        return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
    }
}
