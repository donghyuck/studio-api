package studio.one.application.attachment.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.one.application.attachment.infrastructure.persistence.model.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.domain.port.AttachmentDownloadUrlIssueAuditLogRepository;
import studio.one.application.attachment.application.command.AttachmentDownloadUrlIssueAuditLogQuery;

@Repository
@RequiredArgsConstructor
public class JdbcAttachmentDownloadUrlIssueAuditLogRepository implements AttachmentDownloadUrlIssueAuditLogRepository {

    private static final String TABLE = "TB_APPLICATION_ATTACHMENT_URL_ISSUE_LOG";
    private static final String DEFAULT_ORDER = " order by ISSUED_AT desc, LOG_ID desc";
    private static final Map<String, String> SORT_COLUMNS = Map.ofEntries(
            Map.entry("logId", "LOG_ID"),
            Map.entry("attachmentId", "ATTACHMENT_ID"),
            Map.entry("objectType", "OBJECT_TYPE"),
            Map.entry("objectId", "OBJECT_ID"),
            Map.entry("endpointKind", "ENDPOINT_KIND"),
            Map.entry("issuedByUserId", "ISSUED_BY_USER_ID"),
            Map.entry("issuedByPrincipalName", "ISSUED_BY_PRINCIPAL_NAME"),
            Map.entry("issuedAt", "ISSUED_AT"),
            Map.entry("expiresAt", "EXPIRES_AT"),
            Map.entry("ttlSeconds", "TTL_SECONDS"),
            Map.entry("linkType", "LINK_TYPE"),
            Map.entry("tokenHash", "TOKEN_HASH"),
            Map.entry("storageProviderId", "STORAGE_PROVIDER_ID"),
            Map.entry("bucket", "BUCKET"),
            Map.entry("objectKeyHash", "OBJECT_KEY_HASH"),
            Map.entry("clientIp", "CLIENT_IP"),
            Map.entry("userAgent", "USER_AGENT"));
    private static final Set<String> SAFE_DIRECTIONS = Set.of("asc", "desc");
    private static final RowMapper<AttachmentDownloadUrlIssueAuditLog> ROW_MAPPER =
            JdbcAttachmentDownloadUrlIssueAuditLogRepository::mapRow;

    private final NamedParameterJdbcTemplate template;

    @Override
    public AttachmentDownloadUrlIssueAuditLog save(AttachmentDownloadUrlIssueAuditLog log) {
        String sql = """
                insert into %s (
                    ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID, ENDPOINT_KIND,
                    ISSUED_BY_USER_ID, ISSUED_BY_PRINCIPAL_NAME,
                    ISSUED_AT, EXPIRES_AT, TTL_SECONDS,
                    LINK_TYPE, TOKEN_HASH,
                    STORAGE_PROVIDER_ID, BUCKET, OBJECT_KEY_HASH,
                    CLIENT_IP, USER_AGENT
                ) values (
                    :attachmentId, :objectType, :objectId, :endpointKind,
                    :issuedByUserId, :issuedByPrincipalName,
                    :issuedAt, :expiresAt, :ttlSeconds,
                    :linkType, :tokenHash,
                    :storageProviderId, :bucket, :objectKeyHash,
                    :clientIp, :userAgent
                )
                """.formatted(TABLE);
        template.update(sql, params(log));
        return log;
    }

    @Override
    public Optional<AttachmentDownloadUrlIssueAuditLog> findByTokenHash(String tokenHash) {
        if (!StringUtils.hasText(tokenHash)) {
            return Optional.empty();
        }
        String sql = """
                select
                    LOG_ID, ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID, ENDPOINT_KIND,
                    ISSUED_BY_USER_ID, ISSUED_BY_PRINCIPAL_NAME,
                    ISSUED_AT, EXPIRES_AT, TTL_SECONDS,
                    LINK_TYPE, TOKEN_HASH,
                    STORAGE_PROVIDER_ID, BUCKET, OBJECT_KEY_HASH,
                    CLIENT_IP, USER_AGENT
                from %s
                where TOKEN_HASH = :tokenHash
                order by ISSUED_AT desc, LOG_ID desc
                limit 1
                """.formatted(TABLE);
        List<AttachmentDownloadUrlIssueAuditLog> logs =
                template.query(sql, Map.of("tokenHash", tokenHash), ROW_MAPPER);
        return logs.stream().findFirst();
    }

    @Override
    public Page<AttachmentDownloadUrlIssueAuditLog> search(
            AttachmentDownloadUrlIssueAuditLogQuery query,
            Pageable pageable) {
        Map<String, Object> params = new HashMap<>();
        String where = buildWhereClause(query, params);
        String countSql = "select count(*) from " + TABLE + where;
        Long total = template.queryForObject(countSql, params, Long.class);
        long totalCount = total == null ? 0L : total;
        if (totalCount == 0L) {
            return Page.empty(pageable == null ? Pageable.unpaged() : pageable);
        }

        String dataSql = """
                select
                    LOG_ID, ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID, ENDPOINT_KIND,
                    ISSUED_BY_USER_ID, ISSUED_BY_PRINCIPAL_NAME,
                    ISSUED_AT, EXPIRES_AT, TTL_SECONDS,
                    LINK_TYPE, TOKEN_HASH,
                    STORAGE_PROVIDER_ID, BUCKET, OBJECT_KEY_HASH,
                    CLIENT_IP, USER_AGENT
                from %s
                """.formatted(TABLE) + where + orderBy(pageable);
        if (pageable != null && pageable.isPaged()) {
            params.put("limit", pageable.getPageSize());
            params.put("offset", pageable.getOffset());
            dataSql += " limit :limit offset :offset";
        }
        List<AttachmentDownloadUrlIssueAuditLog> content = template.query(dataSql, params, ROW_MAPPER);
        return new PageImpl<>(content, pageable == null ? Pageable.unpaged() : pageable, totalCount);
    }

    private Map<String, Object> params(AttachmentDownloadUrlIssueAuditLog log) {
        Map<String, Object> params = new HashMap<>();
        params.put("attachmentId", log.getAttachmentId());
        params.put("objectType", log.getObjectType());
        params.put("objectId", log.getObjectId());
        params.put("endpointKind", log.getEndpointKind());
        params.put("issuedByUserId", log.getIssuedByUserId());
        params.put("issuedByPrincipalName", log.getIssuedByPrincipalName());
        params.put("issuedAt", Timestamp.from(log.getIssuedAt()));
        params.put("expiresAt", Timestamp.from(log.getExpiresAt()));
        params.put("ttlSeconds", log.getTtlSeconds());
        params.put("linkType", log.getLinkType());
        params.put("tokenHash", log.getTokenHash());
        params.put("storageProviderId", log.getStorageProviderId());
        params.put("bucket", log.getBucket());
        params.put("objectKeyHash", log.getObjectKeyHash());
        params.put("clientIp", log.getClientIp());
        params.put("userAgent", log.getUserAgent());
        return params;
    }

    private String buildWhereClause(AttachmentDownloadUrlIssueAuditLogQuery query, Map<String, Object> params) {
        StringBuilder where = new StringBuilder(" where 1=1");
        if (query == null) {
            return where.toString();
        }
        if (query.attachmentId() != null) {
            where.append(" and ATTACHMENT_ID = :attachmentId");
            params.put("attachmentId", query.attachmentId());
        }
        if (query.objectType() != null) {
            where.append(" and OBJECT_TYPE = :objectType");
            params.put("objectType", query.objectType());
        }
        if (query.objectId() != null) {
            where.append(" and OBJECT_ID = :objectId");
            params.put("objectId", query.objectId());
        }
        if (query.endpointKind() != null) {
            where.append(" and ENDPOINT_KIND = :endpointKind");
            params.put("endpointKind", query.endpointKind().name());
        }
        if (StringUtils.hasText(query.issuedByPrincipalName())) {
            where.append(" and lower(ISSUED_BY_PRINCIPAL_NAME) like :issuedByPrincipalName");
            params.put("issuedByPrincipalName", "%" + query.issuedByPrincipalName().toLowerCase(Locale.ROOT) + "%");
        }
        if (query.from() != null) {
            where.append(" and ISSUED_AT >= :from");
            params.put("from", Timestamp.from(query.from()));
        }
        if (query.to() != null) {
            where.append(" and ISSUED_AT < :to");
            params.put("to", Timestamp.from(query.to()));
        }
        return where.toString();
    }

    private String orderBy(Pageable pageable) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return DEFAULT_ORDER;
        }
        List<String> orders = pageable.getSort().stream()
                .map(this::orderExpression)
                .filter(StringUtils::hasText)
                .toList();
        return orders.isEmpty() ? DEFAULT_ORDER : " order by " + String.join(", ", orders);
    }

    private String orderExpression(Sort.Order order) {
        String column = SORT_COLUMNS.get(order.getProperty());
        if (!StringUtils.hasText(column)) {
            return null;
        }
        String direction = order.getDirection().name().toLowerCase(Locale.ROOT);
        if (!SAFE_DIRECTIONS.contains(direction)) {
            return null;
        }
        return column + " " + direction;
    }

    private static AttachmentDownloadUrlIssueAuditLog mapRow(ResultSet rs, int rowNum) throws SQLException {
        AttachmentDownloadUrlIssueAuditLog log = new AttachmentDownloadUrlIssueAuditLog();
        log.setLogId(rs.getLong("LOG_ID"));
        log.setAttachmentId(rs.getLong("ATTACHMENT_ID"));
        log.setObjectType(rs.getInt("OBJECT_TYPE"));
        log.setObjectId(rs.getLong("OBJECT_ID"));
        log.setEndpointKind(rs.getString("ENDPOINT_KIND"));
        log.setIssuedByUserId(nullableLong(rs, "ISSUED_BY_USER_ID"));
        log.setIssuedByPrincipalName(rs.getString("ISSUED_BY_PRINCIPAL_NAME"));
        log.setIssuedAt(rs.getTimestamp("ISSUED_AT").toInstant());
        log.setExpiresAt(rs.getTimestamp("EXPIRES_AT").toInstant());
        log.setTtlSeconds(rs.getLong("TTL_SECONDS"));
        log.setLinkType(rs.getString("LINK_TYPE"));
        log.setTokenHash(rs.getString("TOKEN_HASH"));
        log.setStorageProviderId(rs.getString("STORAGE_PROVIDER_ID"));
        log.setBucket(rs.getString("BUCKET"));
        log.setObjectKeyHash(rs.getString("OBJECT_KEY_HASH"));
        log.setClientIp(rs.getString("CLIENT_IP"));
        log.setUserAgent(rs.getString("USER_AGENT"));
        return log;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
