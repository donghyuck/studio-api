package studio.one.base.security.audit.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import studio.one.base.security.audit.domain.entity.LoginFailureLog;
import studio.one.base.security.audit.persistence.LoginFailureLogRepository;
import studio.one.base.security.audit.service.LoginFailQuery;
import studio.one.platform.data.sqlquery.annotation.SqlStatement;

@Repository
public class LoginFailureLogJdbcRepository implements LoginFailureLogRepository {

    private static final RowMapper<LoginFailureLog> ROW_MAPPER = (rs, rowNum) -> LoginFailureLog.builder()
            .id(rs.getLong("id"))
            .username(rs.getString("username"))
            .remoteIp(rs.getString("remote_ip"))
            .userAgent(rs.getString("user_agent"))
            .failureType(rs.getString("failure_type"))
            .message(rs.getString("message"))
            .occurredAt(rs.getTimestamp("occurred_at").toInstant())
            .build();

    private final NamedParameterJdbcTemplate template;

    @SqlStatement("security.loginFailureLogInsert")
    private String insertSql;

    @SqlStatement("security.loginFailureLogUpdate")
    private String updateSql;

    @SqlStatement("security.loginFailureLogDeleteOlderThan")
    private String deleteOlderThanSql;

    @SqlStatement("security.loginFailureLogCountByUsernameSince")
    private String countByUsernameSinceSql;

    @SqlStatement("security.loginFailureLogCountSearchBase")
    private String countSearchBaseSql;

    @SqlStatement("security.loginFailureLogSearchBase")
    private String searchBaseSql;

    public LoginFailureLogJdbcRepository(NamedParameterJdbcTemplate template) {
        this.template = template;
    }

    @Override
    public LoginFailureLog save(LoginFailureLog log) {
        if (log.getId() == null) {
            return insert(log);
        }
        return update(log);
    }

    @Override
    public long deleteOlderThan(Instant cutoff) {
        return template.update(deleteOlderThanSql, Map.of("cutoff", Timestamp.from(cutoff)));
    }

    @Override
    public long countByUsernameSince(String username, Instant since) {
        Long count = template.queryForObject(countByUsernameSinceSql, Map.of(
                "username", username,
                "since", Timestamp.from(since)), Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public Page<LoginFailureLog> search(LoginFailQuery query, Pageable pageable) {
        Map<String, Object> params = new HashMap<>();
        String where = buildWhereClause(query, params);
        String countSql = countSearchBaseSql + where;
        long total = template.queryForObject(countSql, params, Long.class);
        if (total == 0) {
            return Page.empty(pageable);
        }
        String order = " order by occurred_at desc";
        if (!pageable.isUnpaged()) {
            params.put("limit", pageable.getPageSize());
            params.put("offset", pageable.getOffset());
        }
        String dataSql = searchBaseSql + where + order;
        if (!pageable.isUnpaged()) {
            dataSql += " limit :limit offset :offset";
        }
        var content = template.query(dataSql, params, ROW_MAPPER);
        return new PageImpl<>(content, pageable, total);
    }

    private LoginFailureLog insert(LoginFailureLog log) {
        Instant occurred = Objects.requireNonNullElseGet(log.getOccurredAt(), Instant::now);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("username", log.getUsername())
                .addValue("remote_ip", log.getRemoteIp())
                .addValue("user_agent", log.getUserAgent())
                .addValue("failure_type", log.getFailureType())
                .addValue("message", log.getMessage())
                .addValue("occurred_at", Timestamp.from(occurred));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        template.update(insertSql, params, keyHolder, new String[] { "id" });
        Number key = keyHolder.getKey();
        if (key != null) {
            log.setId(key.longValue());
        }
        log.setOccurredAt(occurred);
        return log;
    }

    private LoginFailureLog update(LoginFailureLog log) {
        Map<String, Object> params = new HashMap<>();
        params.put("username", log.getUsername());
        params.put("remote_ip", log.getRemoteIp());
        params.put("user_agent", log.getUserAgent());
        params.put("failure_type", log.getFailureType());
        params.put("message", log.getMessage());
        params.put("occurred_at", Timestamp.from(log.getOccurredAt()));
        params.put("id", log.getId());
        template.update(updateSql, params);
        return log;
    }

    private String buildWhereClause(LoginFailQuery query, Map<String, Object> params) {
        StringBuilder where = new StringBuilder(" where 1=1");
        if (query == null) {
            return where.toString();
        }
        if (query.getUsernameLike() != null && !query.getUsernameLike().isBlank()) {
            where.append(" and lower(username) like :usernameLike");
            params.put("usernameLike", "%" + query.getUsernameLike().toLowerCase() + "%");
        }
        if (query.getIpEquals() != null && !query.getIpEquals().isBlank()) {
            where.append(" and remote_ip = :remoteIp");
            params.put("remoteIp", query.getIpEquals());
        }
        if (query.getFailureType() != null && !query.getFailureType().isBlank()) {
            where.append(" and failure_type = :failureType");
            params.put("failureType", query.getFailureType());
        }
        if (query.getFrom() != null) {
            Instant from = query.getFrom().toInstant();
            where.append(" and occurred_at >= :from");
            params.put("from", Timestamp.from(from));
        }
        if (query.getTo() != null) {
            Instant to = query.getTo().toInstant();
            where.append(" and occurred_at < :to");
            params.put("to", Timestamp.from(to));
        }
        return where.toString();
    }
}
