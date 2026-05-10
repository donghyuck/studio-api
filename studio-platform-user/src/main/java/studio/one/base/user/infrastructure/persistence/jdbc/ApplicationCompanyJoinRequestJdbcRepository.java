package studio.one.base.user.infrastructure.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.model.company.CompanyJoinRequestStatus;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.model.ApplicationCompanyJoinRequest;
import studio.one.base.user.domain.port.ApplicationCompanyJoinRequestRepository;

@Repository(ApplicationCompanyJoinRequestRepository.SERVICE_NAME)
public class ApplicationCompanyJoinRequestJdbcRepository extends BaseJdbcRepository implements ApplicationCompanyJoinRequestRepository {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "requestId", "REQUEST_ID",
            "companyId", "COMPANY_ID",
            "keyId", "KEY_ID",
            "userId", "USER_ID",
            "status", "STATUS",
            "requestedAt", "REQUESTED_AT",
            "decidedAt", "DECIDED_AT");

    private static final RowMapper<ApplicationCompanyJoinRequest> REQUEST_ROW_MAPPER = (rs, rowNum) -> {
        ApplicationCompanyJoinRequest request = new ApplicationCompanyJoinRequest();
        request.setRequestId(rs.getLong("REQUEST_ID"));
        request.setCompanyId(rs.getLong("COMPANY_ID"));
        request.setKeyId(rs.getLong("KEY_ID"));
        long userId = rs.getLong("USER_ID");
        request.setUserId(rs.wasNull() ? null : userId);
        request.setName(rs.getString("REQUEST_NAME"));
        request.setEmail(rs.getString("EMAIL"));
        request.setMessage(rs.getString("MESSAGE"));
        request.setRequestedRole(CompanyRole.valueOf(rs.getString("REQUESTED_ROLE")));
        request.setStatus(CompanyJoinRequestStatus.valueOf(rs.getString("STATUS")));
        Timestamp requestedAt = rs.getTimestamp("REQUESTED_AT");
        Timestamp decidedAt = rs.getTimestamp("DECIDED_AT");
        Timestamp updatedAt = rs.getTimestamp("UPDATED_AT");
        request.setRequestedAt(requestedAt == null ? null : requestedAt.toInstant());
        request.setDecidedAt(decidedAt == null ? null : decidedAt.toInstant());
        request.setUpdatedAt(updatedAt == null ? null : updatedAt.toInstant());
        long requestedBy = rs.getLong("REQUESTED_BY");
        request.setRequestedBy(rs.wasNull() ? null : requestedBy);
        long decidedBy = rs.getLong("DECIDED_BY");
        request.setDecidedBy(rs.wasNull() ? null : decidedBy);
        return request;
    };

    public ApplicationCompanyJoinRequestJdbcRepository(NamedParameterJdbcTemplate namedTemplate) {
        super(namedTemplate);
    }

    @Override
    public Page<ApplicationCompanyJoinRequest> findAllByCompanyId(Long companyId, CompanyJoinRequestStatus status, Pageable pageable) {
        Map<String, Object> params = new HashMap<>();
        params.put("companyId", companyId);
        String where = " where COMPANY_ID = :companyId";
        if (status != null) {
            params.put("status", status.name());
            where += " and STATUS = :status";
        }
        return queryPage(selectSql() + where, "select count(*) from TB_APPLICATION_COMPANY_JOIN_REQUEST" + where,
                params, pageable, REQUEST_ROW_MAPPER, "REQUESTED_AT desc", SORT_COLUMNS);
    }

    @Override
    public Optional<ApplicationCompanyJoinRequest> findById(Long requestId) {
        return queryOptional(selectSql() + " where REQUEST_ID = :requestId", Map.of("requestId", requestId), REQUEST_ROW_MAPPER);
    }

    @Override
    public Optional<ApplicationCompanyJoinRequest> findForUpdateById(Long requestId) {
        return queryOptional(selectSql() + " where REQUEST_ID = :requestId for update", Map.of("requestId", requestId), REQUEST_ROW_MAPPER);
    }

    @Override
    public boolean existsPendingByKeyIdAndUserId(Long keyId, Long userId) {
        String sql = """
                select count(*)
                  from TB_APPLICATION_COMPANY_JOIN_REQUEST
                 where KEY_ID = :keyId
                   and USER_ID = :userId
                   and STATUS = 'PENDING'
                """;
        Long count = namedTemplate.queryForObject(sql, Map.of("keyId", keyId, "userId", userId), Long.class);
        return count != null && count > 0;
    }

    @Override
    public boolean existsPendingByCompanyIdAndUserId(Long companyId, Long userId) {
        String sql = """
                select count(*)
                  from TB_APPLICATION_COMPANY_JOIN_REQUEST
                 where COMPANY_ID = :companyId
                   and USER_ID = :userId
                   and STATUS = 'PENDING'
                """;
        Long count = namedTemplate.queryForObject(sql, Map.of("companyId", companyId, "userId", userId), Long.class);
        return count != null && count > 0;
    }

    @Override
    public long countPendingByKeyId(Long keyId) {
        String sql = """
                select count(*)
                  from TB_APPLICATION_COMPANY_JOIN_REQUEST
                 where KEY_ID = :keyId
                   and STATUS = 'PENDING'
                   and USER_ID is not null
                """;
        Long count = namedTemplate.queryForObject(sql, Map.of("keyId", keyId), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public ApplicationCompanyJoinRequest save(ApplicationCompanyJoinRequest request) {
        if (request.getRequestId() == null) {
            return insert(request);
        }
        update(request);
        return findById(request.getRequestId()).orElse(request);
    }

    private ApplicationCompanyJoinRequest insert(ApplicationCompanyJoinRequest request) {
        Instant now = Instant.now();
        if (request.getRequestedAt() == null) {
            request.setRequestedAt(now);
        }
        if (request.getUpdatedAt() == null) {
            request.setUpdatedAt(request.getRequestedAt());
        }
        if (request.getStatus() == null) {
            request.setStatus(CompanyJoinRequestStatus.PENDING);
        }
        String sql = """
                insert into TB_APPLICATION_COMPANY_JOIN_REQUEST
                    (COMPANY_ID, KEY_ID, USER_ID, REQUEST_NAME, EMAIL, MESSAGE, REQUESTED_ROLE, STATUS,
                     REQUESTED_AT, REQUESTED_BY, DECIDED_AT, DECIDED_BY, UPDATED_AT)
                values
                    (:companyId, :keyId, :userId, :name, :email, :message, :requestedRole, :status,
                     :requestedAt, :requestedBy, :decidedAt, :decidedBy, :updatedAt)
                """;
        KeyHolder holder = new GeneratedKeyHolder();
        namedTemplate.update(sql, new MapSqlParameterSource(params(request)), holder, new String[] { "request_id" });
        Number generated = holder.getKey();
        if (generated != null) {
            request.setRequestId(generated.longValue());
        }
        return request.getRequestId() == null ? request : findById(request.getRequestId()).orElse(request);
    }

    private void update(ApplicationCompanyJoinRequest request) {
        request.setUpdatedAt(Instant.now());
        String sql = """
                update TB_APPLICATION_COMPANY_JOIN_REQUEST
                   set USER_ID = :userId,
                       REQUEST_NAME = :name,
                       EMAIL = :email,
                       MESSAGE = :message,
                       REQUESTED_ROLE = :requestedRole,
                       STATUS = :status,
                       DECIDED_AT = :decidedAt,
                       DECIDED_BY = :decidedBy,
                       UPDATED_AT = :updatedAt
                 where REQUEST_ID = :requestId
                """;
        namedTemplate.update(sql, params(request));
    }

    private Map<String, Object> params(ApplicationCompanyJoinRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("requestId", request.getRequestId());
        params.put("companyId", request.getCompanyId() != null ? request.getCompanyId() : request.getCompany().getCompanyId());
        params.put("keyId", request.getKeyId() != null ? request.getKeyId() : request.getMemberKey().getKeyId());
        params.put("userId", request.getUserId());
        params.put("name", request.getName());
        params.put("email", request.getEmail());
        params.put("message", request.getMessage());
        params.put("requestedRole", request.getRequestedRole().name());
        params.put("status", request.getStatus().name());
        params.put("requestedAt", Timestamp.from(request.getRequestedAt()));
        params.put("requestedBy", request.getRequestedBy());
        params.put("decidedAt", request.getDecidedAt() == null ? null : Timestamp.from(request.getDecidedAt()));
        params.put("decidedBy", request.getDecidedBy());
        params.put("updatedAt", Timestamp.from(request.getUpdatedAt()));
        return params;
    }

    private String selectSql() {
        return """
                select REQUEST_ID, COMPANY_ID, KEY_ID, USER_ID, REQUEST_NAME, EMAIL, MESSAGE, REQUESTED_ROLE,
                       STATUS, REQUESTED_AT, REQUESTED_BY, DECIDED_AT, DECIDED_BY, UPDATED_AT
                  from TB_APPLICATION_COMPANY_JOIN_REQUEST
                """;
    }
}
