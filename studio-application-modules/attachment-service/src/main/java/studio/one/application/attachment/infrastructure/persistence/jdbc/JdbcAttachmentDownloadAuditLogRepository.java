package studio.one.application.attachment.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import studio.one.application.attachment.domain.model.AttachmentDownloadAuditLog;
import studio.one.application.attachment.domain.model.AttachmentDownloadAuditLogCount;
import studio.one.application.attachment.domain.port.AttachmentDownloadAuditLogRepository;
import studio.one.application.attachment.application.command.AttachmentDownloadAuditLogQuery;

@Repository
@RequiredArgsConstructor
public class JdbcAttachmentDownloadAuditLogRepository implements AttachmentDownloadAuditLogRepository {

    private static final String TABLE = "TB_APPLICATION_ATTACHMENT_DOWNLOAD_LOG";
    private static final String DEFAULT_ORDER = " order by REQUESTED_AT desc, DOWNLOAD_LOG_ID desc";
    private static final Map<String, String> SORT_COLUMNS = Map.ofEntries(
            Map.entry("downloadLogId", "DOWNLOAD_LOG_ID"),
            Map.entry("issueLogId", "ISSUE_LOG_ID"),
            Map.entry("tokenHash", "TOKEN_HASH"),
            Map.entry("attachmentId", "ATTACHMENT_ID"),
            Map.entry("objectType", "OBJECT_TYPE"),
            Map.entry("objectId", "OBJECT_ID"),
            Map.entry("linkType", "LINK_TYPE"),
            Map.entry("requestedAt", "REQUESTED_AT"),
            Map.entry("result", "RESULT"),
            Map.entry("httpStatus", "HTTP_STATUS"),
            Map.entry("downloadedBytes", "DOWNLOADED_BYTES"),
            Map.entry("clientIp", "CLIENT_IP"),
            Map.entry("userAgent", "USER_AGENT"),
            Map.entry("errorCode", "ERROR_CODE"));
    private static final Set<String> SAFE_DIRECTIONS = Set.of("asc", "desc");
    private static final RowMapper<AttachmentDownloadAuditLog> ROW_MAPPER =
            JdbcAttachmentDownloadAuditLogRepository::mapRow;

    private final NamedParameterJdbcTemplate template;

    @Override
    public AttachmentDownloadAuditLog save(AttachmentDownloadAuditLog log) {
        String sql = """
                insert into %s (
                    ISSUE_LOG_ID, TOKEN_HASH, ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID,
                    LINK_TYPE, REQUESTED_AT, RESULT, HTTP_STATUS, DOWNLOADED_BYTES,
                    CLIENT_IP, USER_AGENT, ERROR_CODE
                ) values (
                    :issueLogId, :tokenHash, :attachmentId, :objectType, :objectId,
                    :linkType, :requestedAt, :result, :httpStatus, :downloadedBytes,
                    :clientIp, :userAgent, :errorCode
                )
                """.formatted(TABLE);
        template.update(sql, params(log));
        return log;
    }

    @Override
    public Page<AttachmentDownloadAuditLog> search(AttachmentDownloadAuditLogQuery query, Pageable pageable) {
        Map<String, Object> params = new HashMap<>();
        String where = buildWhereClause(query, params);
        Long total = template.queryForObject("select count(*) from " + TABLE + where, params, Long.class);
        long totalCount = total == null ? 0L : total;
        if (totalCount == 0L) {
            return Page.empty(pageable == null ? Pageable.unpaged() : pageable);
        }

        String dataSql = """
                select
                    DOWNLOAD_LOG_ID, ISSUE_LOG_ID, TOKEN_HASH, ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID,
                    LINK_TYPE, REQUESTED_AT, RESULT, HTTP_STATUS, DOWNLOADED_BYTES,
                    CLIENT_IP, USER_AGENT, ERROR_CODE
                from %s
                """.formatted(TABLE) + where + orderBy(pageable);
        if (pageable != null && pageable.isPaged()) {
            params.put("limit", pageable.getPageSize());
            params.put("offset", pageable.getOffset());
            dataSql += " limit :limit offset :offset";
        }
        List<AttachmentDownloadAuditLog> content = template.query(dataSql, params, ROW_MAPPER);
        return new PageImpl<>(content, pageable == null ? Pageable.unpaged() : pageable, totalCount);
    }

    @Override
    public List<AttachmentDownloadAuditLogCount> countByIssueLogIdsOrTokenHashes(
            Collection<Long> issueLogIds,
            Collection<String> tokenHashes) {
        if ((issueLogIds == null || issueLogIds.isEmpty()) && (tokenHashes == null || tokenHashes.isEmpty())) {
            return List.of();
        }
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder();
        if (issueLogIds != null && !issueLogIds.isEmpty()) {
            where.append("ISSUE_LOG_ID in (:issueLogIds)");
            params.put("issueLogIds", issueLogIds);
        }
        if (tokenHashes != null && !tokenHashes.isEmpty()) {
            if (!where.isEmpty()) {
                where.append(" or ");
            }
            where.append("TOKEN_HASH in (:tokenHashes)");
            params.put("tokenHashes", tokenHashes);
        }
        String sql = """
                select ISSUE_LOG_ID, TOKEN_HASH, count(*) as DOWNLOAD_COUNT
                from %s
                where %s
                group by ISSUE_LOG_ID, TOKEN_HASH
                """.formatted(TABLE, where);
        return template.query(sql, params, (rs, rowNum) -> new AttachmentDownloadAuditLogCount(
                nullableLong(rs, "ISSUE_LOG_ID"),
                rs.getString("TOKEN_HASH"),
                rs.getLong("DOWNLOAD_COUNT")));
    }

    private Map<String, Object> params(AttachmentDownloadAuditLog log) {
        Map<String, Object> params = new HashMap<>();
        params.put("issueLogId", log.getIssueLogId());
        params.put("tokenHash", log.getTokenHash());
        params.put("attachmentId", log.getAttachmentId());
        params.put("objectType", log.getObjectType());
        params.put("objectId", log.getObjectId());
        params.put("linkType", log.getLinkType());
        params.put("requestedAt", Timestamp.from(log.getRequestedAt()));
        params.put("result", log.getResult());
        params.put("httpStatus", log.getHttpStatus());
        params.put("downloadedBytes", log.getDownloadedBytes());
        params.put("clientIp", log.getClientIp());
        params.put("userAgent", log.getUserAgent());
        params.put("errorCode", log.getErrorCode());
        return params;
    }

    private String buildWhereClause(AttachmentDownloadAuditLogQuery query, Map<String, Object> params) {
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
        if (StringUtils.hasText(query.tokenHash())) {
            where.append(" and TOKEN_HASH = :tokenHash");
            params.put("tokenHash", query.tokenHash());
        }
        if (query.result() != null) {
            where.append(" and RESULT = :result");
            params.put("result", query.result().name());
        }
        if (query.from() != null) {
            where.append(" and REQUESTED_AT >= :from");
            params.put("from", Timestamp.from(query.from()));
        }
        if (query.to() != null) {
            where.append(" and REQUESTED_AT < :to");
            params.put("to", Timestamp.from(query.to()));
        }
        if (StringUtils.hasText(query.clientIp())) {
            where.append(" and CLIENT_IP = :clientIp");
            params.put("clientIp", query.clientIp());
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

    private static AttachmentDownloadAuditLog mapRow(ResultSet rs, int rowNum) throws SQLException {
        AttachmentDownloadAuditLog log = new AttachmentDownloadAuditLog();
        log.setDownloadLogId(rs.getLong("DOWNLOAD_LOG_ID"));
        log.setIssueLogId(nullableLong(rs, "ISSUE_LOG_ID"));
        log.setTokenHash(rs.getString("TOKEN_HASH"));
        log.setAttachmentId(nullableLong(rs, "ATTACHMENT_ID"));
        log.setObjectType(nullableInteger(rs, "OBJECT_TYPE"));
        log.setObjectId(nullableLong(rs, "OBJECT_ID"));
        log.setLinkType(rs.getString("LINK_TYPE"));
        log.setRequestedAt(rs.getTimestamp("REQUESTED_AT").toInstant());
        log.setResult(rs.getString("RESULT"));
        log.setHttpStatus(rs.getInt("HTTP_STATUS"));
        log.setDownloadedBytes(nullableLong(rs, "DOWNLOADED_BYTES"));
        log.setClientIp(rs.getString("CLIENT_IP"));
        log.setUserAgent(rs.getString("USER_AGENT"));
        log.setErrorCode(rs.getString("ERROR_CODE"));
        return log;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
