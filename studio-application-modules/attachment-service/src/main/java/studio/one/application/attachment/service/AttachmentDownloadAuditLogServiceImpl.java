package studio.one.application.attachment.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.one.application.attachment.domain.entity.AttachmentDownloadAuditLog;
import studio.one.application.attachment.domain.entity.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.persistence.AttachmentDownloadAuditLogRepository;
import studio.one.application.attachment.persistence.AttachmentDownloadUrlIssueAuditLogRepository;

@RequiredArgsConstructor
public class AttachmentDownloadAuditLogServiceImpl implements AttachmentDownloadAuditLogService {

    private static final String LINK_TYPE_APPLICATION_SIGNED = "APPLICATION_SIGNED";
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("requestedAt"),
            Sort.Order.desc("downloadLogId"));
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "downloadLogId",
            "issueLogId",
            "tokenHash",
            "attachmentId",
            "objectType",
            "objectId",
            "linkType",
            "requestedAt",
            "result",
            "httpStatus",
            "downloadedBytes",
            "clientIp",
            "userAgent",
            "errorCode");

    private final AttachmentDownloadAuditLogRepository downloadLogRepository;
    private final AttachmentDownloadUrlIssueAuditLogRepository issueLogRepository;
    private final Clock clock;

    public AttachmentDownloadAuditLogServiceImpl(
            AttachmentDownloadAuditLogRepository downloadLogRepository,
            AttachmentDownloadUrlIssueAuditLogRepository issueLogRepository) {
        this(downloadLogRepository, issueLogRepository, Clock.systemUTC());
    }

    @Override
    @Transactional
    public AttachmentDownloadAuditLog record(AttachmentDownloadAuditLogCommand command) {
        AttachmentDownloadUrlIssueAuditLog issueLog = findIssueLog(command.tokenHash());
        AttachmentDownloadAuditLog log = new AttachmentDownloadAuditLog();
        log.setIssueLogId(issueLog == null ? null : issueLog.getLogId());
        log.setTokenHash(limit(command.tokenHash(), 64));
        log.setAttachmentId(firstNonNull(command.attachmentId(), issueLog == null ? null : issueLog.getAttachmentId()));
        log.setObjectType(firstNonNull(command.objectType(), issueLog == null ? null : issueLog.getObjectType()));
        log.setObjectId(firstNonNull(command.objectId(), issueLog == null ? null : issueLog.getObjectId()));
        log.setLinkType(firstText(command.linkType(), issueLog == null ? null : issueLog.getLinkType(),
                LINK_TYPE_APPLICATION_SIGNED));
        log.setRequestedAt(command.requestedAt() == null ? clock.instant() : command.requestedAt());
        log.setResult(command.result().name());
        log.setHttpStatus(command.httpStatus());
        log.setDownloadedBytes(command.downloadedBytes());
        log.setClientIp(limit(command.clientIp(), 45));
        log.setUserAgent(limit(command.userAgent(), 512));
        log.setErrorCode(limit(command.errorCode(), 80));
        return downloadLogRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AttachmentDownloadAuditLog> find(AttachmentDownloadAuditLogQuery query, Pageable pageable) {
        return downloadLogRepository.search(query, normalize(pageable));
    }

    private AttachmentDownloadUrlIssueAuditLog findIssueLog(String tokenHash) {
        if (!StringUtils.hasText(tokenHash)) {
            return null;
        }
        return issueLogRepository.findByTokenHash(tokenHash).orElse(null);
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

    private static <T> T firstNonNull(T first, T second) {
        return first == null ? second : first;
    }

    private static String firstText(String first, String second, String fallback) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        if (StringUtils.hasText(second)) {
            return second;
        }
        return fallback;
    }

    private static String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
