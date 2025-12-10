package studio.one.application.mail.service.impl;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import studio.one.application.mail.domain.model.DefaultMailSyncLog;
import studio.one.application.mail.domain.model.MailSyncLog;
import studio.one.application.mail.service.MailSyncLogService;
import studio.one.platform.data.jdbc.PagingJdbcTemplate;
import studio.one.platform.data.sqlquery.annotation.SqlStatement;
import studio.one.platform.exception.NotFoundException;

@Transactional
@Service(MailSyncLogService.SERVICE_NAME)
public class JdbcMailSyncLogService implements MailSyncLogService {

    @SqlStatement("data.mail.syncLog.insert")
    private String insertSql;

    @SqlStatement("data.mail.syncLog.update")
    private String updateSql;

    @SqlStatement("data.mail.syncLog.recent")
    private String recentSql;

    @SqlStatement("data.mail.syncLog.countAll")
    private String countSql;

    @SqlStatement("data.mail.syncLog.findPage")
    private String findPageSql;

    @SqlStatement("data.mail.syncLog.findById")
    private String findByIdSql;

    private static final Map<String, String> SORT_COLUMNS = Map.ofEntries(
            Map.entry("logId", "LOG_ID"),
            Map.entry("startedAt", "STARTED_AT"),
            Map.entry("finishedAt", "FINISHED_AT"),
            Map.entry("processed", "PROCESSED"),
            Map.entry("succeeded", "SUCCEEDED"),
            Map.entry("failed", "FAILED"),
            Map.entry("status", "STATUS"),
            Map.entry("message", "MESSAGE"),
            Map.entry("triggeredBy", "TRIGGERED_BY"));

    private static final RowMapper<MailSyncLog> ROW_MAPPER = (rs, rowNum) -> {
        DefaultMailSyncLog log = new DefaultMailSyncLog();
        log.setLogId(rs.getLong("LOG_ID"));
        Timestamp started = rs.getTimestamp("STARTED_AT");
        Timestamp finished = rs.getTimestamp("FINISHED_AT");
        log.setStartedAt(started != null ? started.toInstant() : null);
        log.setFinishedAt(finished != null ? finished.toInstant() : null);
        log.setProcessed(rs.getInt("PROCESSED"));
        log.setSucceeded(rs.getInt("SUCCEEDED"));
        log.setFailed(rs.getInt("FAILED"));
        log.setStatus(rs.getString("STATUS"));
        log.setMessage(rs.getString("MESSAGE"));
        log.setTriggeredBy(rs.getString("TRIGGERED_BY"));
        return log;
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PagingJdbcTemplate pagingJdbcTemplate;

    public JdbcMailSyncLogService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.pagingJdbcTemplate = new PagingJdbcTemplate(jdbcTemplate.getJdbcTemplate().getDataSource());
    }

    @Override
    public MailSyncLog start(String triggeredBy) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("startedAt", Timestamp.from(Instant.now()))
                .addValue("status", "running")
                .addValue("triggeredBy", triggeredBy);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(insertSql, params, keyHolder, new String[] { "LOG_ID" });
        Number key = keyHolder.getKey();
        DefaultMailSyncLog log = new DefaultMailSyncLog();
        log.setLogId(key != null ? key.longValue() : 0);
        log.setStartedAt(params.getValue("startedAt") != null ? ((Timestamp) params.getValue("startedAt")).toInstant()
                : Instant.now());
        log.setStatus("running");
        log.setTriggeredBy(triggeredBy);
        return log;
    }

    @Override
    public void complete(long logId, int processed, int succeeded, int failed, String status, String message) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("logId", logId)
                .addValue("finishedAt", Timestamp.from(Instant.now()))
                .addValue("processed", processed)
                .addValue("succeeded", succeeded)
                .addValue("failed", failed)
                .addValue("status", status)
                .addValue("message", message);
        jdbcTemplate.update(updateSql, params);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MailSyncLog> recent(int limit) {
        Map<String, Object> params = Map.of("limit", limit <= 0 ? 50 : limit);
        return jdbcTemplate.query(recentSql, params, ROW_MAPPER)
                .stream()
                .limit(limit <= 0 ? 50 : limit)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MailSyncLog> page(Pageable pageable) {
        int pageIndex = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.max(1, pageable.getPageSize());
        int offset = pageIndex * pageSize;
        Sort sortToUse = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Order.desc("logId"));
        String orderedQuery = findPageSql + buildOrderByClause(sortToUse, "logId", SORT_COLUMNS);
        List<MailSyncLog> content = pagingJdbcTemplate.queryPage(orderedQuery, offset, pageSize, ROW_MAPPER);
        long total = jdbcTemplate.queryForObject(countSql, Map.of(), Long.class);
        return new PageImpl<>(content, PageRequest.of(pageIndex, pageSize, sortToUse), total);
    }

    @Override
    @Transactional(readOnly = true)
    public MailSyncLog get(long logId) {
        MailSyncLog log = jdbcTemplate.query(findByIdSql, Map.of("logId", logId), ROW_MAPPER)
                .stream()
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("mailSyncLog", logId));
        return log;
    }

    private String buildOrderByClause(Sort sort, String defaultProperty, Map<String, String> propertyToColumn) {
        Sort sortToUse = (sort == null || sort.isUnsorted())
                ? Sort.by(Sort.Order.desc(defaultProperty))
                : sort;
        StringBuilder orderBy = new StringBuilder(" order by ");
        boolean first = true;
        for (Sort.Order order : sortToUse) {
            if (!first) {
                orderBy.append(", ");
            }
            first = false;
            String column = resolveColumn(order.getProperty(), propertyToColumn, defaultProperty);
            orderBy.append(column).append(order.isAscending() ? " asc" : " desc");
        }
        return orderBy.toString();
    }

    private String resolveColumn(String property, Map<String, String> mapping, String defaultProperty) {
        if (property != null && mapping != null && mapping.containsKey(property)) {
            return mapping.get(property);
        }
        if (property == null || property.isBlank()) {
            return mapping != null && mapping.containsKey(defaultProperty)
                    ? mapping.get(defaultProperty)
                    : toSnakeUpper(defaultProperty);
        }
        String snake = toSnakeUpper(property);
        if (mapping != null && mapping.containsKey(defaultProperty)) {
            return mapping.get(defaultProperty);
        }
        return snake;
    }

    private String toSnakeUpper(String property) {
        return property
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }
}
