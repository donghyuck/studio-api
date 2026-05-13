package studio.one.base.security.audit.infrastructure.persistence.jdbc;

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

import studio.one.base.security.audit.IpAddressLiterals;
import studio.one.base.security.audit.domain.model.LoginFailureLog;
import studio.one.base.security.audit.domain.port.LoginFailureLogRepository;
import studio.one.base.security.audit.application.command.LoginFailQuery;

@Repository
public class LoginFailureLogJdbcRepository implements LoginFailureLogRepository {

    private static final String INSERT_SQL = "insert into TB_LOGIN_FAILURE_LOG\n"
                + "    (USERNAME, REMOTE_IP, USER_AGENT, FAILURE_TYPE, MESSAGE, OCCURRED_AT)\n"
                + "values\n"
                + "    (:username, :remote_ip::inet, :user_agent, :failure_type, :message, :occurred_at)\n"
                + "returning ID\n";

    private static final String UPDATE_SQL = "update TB_LOGIN_FAILURE_LOG\n"
                + "   set USERNAME = :username,\n"
                + "       REMOTE_IP = :remote_ip::inet,\n"
                + "       USER_AGENT = :user_agent,\n"
                + "       FAILURE_TYPE = :failure_type,\n"
                + "       MESSAGE = :message,\n"
                + "       OCCURRED_AT = :occurred_at\n"
                + " where ID = :id\n";

    private static final String DELETE_OLDER_THAN_SQL = "delete from TB_LOGIN_FAILURE_LOG\n"
                + " where OCCURRED_AT < :cutoff\n";

    private static final String COUNT_BY_USERNAME_SINCE_SQL = "select count(*)\n"
                + "  from TB_LOGIN_FAILURE_LOG\n"
                + " where USERNAME = :username\n"
                + "   and OCCURRED_AT >= :since\n";

    private static final String COUNT_SEARCH_BASE_SQL = "select count(*)\n"
                + "  from TB_LOGIN_FAILURE_LOG\n";

    private static final String SEARCH_BASE_SQL = "select ID, USERNAME, REMOTE_IP, USER_AGENT, FAILURE_TYPE, MESSAGE, OCCURRED_AT\n"
                + "  from TB_LOGIN_FAILURE_LOG\n";

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
        return template.update(DELETE_OLDER_THAN_SQL, Map.of("cutoff", Timestamp.from(cutoff)));
    }

    @Override
    public long countByUsernameSince(String username, Instant since) {
        Long count = template.queryForObject(COUNT_BY_USERNAME_SINCE_SQL, Map.of(
                "username", username,
                "since", Timestamp.from(since)), Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public Page<LoginFailureLog> search(LoginFailQuery query, Pageable pageable) {
        Map<String, Object> params = new HashMap<>();
        String where = buildWhereClause(query, params);
        String countSql = COUNT_SEARCH_BASE_SQL + where;
        long total = template.queryForObject(countSql, params, Long.class);
        if (total == 0) {
            return Page.empty(pageable);
        }
        String order = " order by occurred_at desc";
        if (!pageable.isUnpaged()) {
            params.put("limit", pageable.getPageSize());
            params.put("offset", pageable.getOffset());
        }
        String dataSql = SEARCH_BASE_SQL + where + order;
        if (!pageable.isUnpaged()) {
            dataSql += " limit :limit offset :offset";
        }
        var content = template.query(dataSql, params, ROW_MAPPER);
        return new PageImpl<>(content, pageable, total);
    }

    private LoginFailureLog insert(LoginFailureLog log) {
        Instant occurred = Objects.requireNonNullElseGet(log.getOccurredAt(), Instant::now);
        log.setOccurredAt(occurred);
        log.sanitizeForPersistence();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("username", log.getUsername())
                .addValue("remote_ip", log.getRemoteIp())
                .addValue("user_agent", log.getUserAgent())
                .addValue("failure_type", log.getFailureType())
                .addValue("message", log.getMessage())
                .addValue("occurred_at", Timestamp.from(occurred));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        template.update(INSERT_SQL, params, keyHolder, new String[] { "id" });
        Number key = keyHolder.getKey();
        if (key != null) {
            log.setId(key.longValue());
        }
        return log;
    }

    private LoginFailureLog update(LoginFailureLog log) {
        log.sanitizeForPersistence();
        Map<String, Object> params = new HashMap<>();
        params.put("username", log.getUsername());
        params.put("remote_ip", log.getRemoteIp());
        params.put("user_agent", log.getUserAgent());
        params.put("failure_type", log.getFailureType());
        params.put("message", log.getMessage());
        params.put("occurred_at", Timestamp.from(log.getOccurredAt()));
        params.put("id", log.getId());
        template.update(UPDATE_SQL, params);
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
            String remoteIp = IpAddressLiterals.normalizeOrNull(query.getIpEquals());
            if (remoteIp == null) {
                throw new IllegalArgumentException("Invalid ipEquals");
            }
            where.append(" and remote_ip = :remoteIp::inet");
            params.put("remoteIp", remoteIp);
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
