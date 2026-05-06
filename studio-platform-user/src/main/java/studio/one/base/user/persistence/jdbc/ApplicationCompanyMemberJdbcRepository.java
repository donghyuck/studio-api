package studio.one.base.user.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import studio.one.base.user.company.model.CompanyMemberStatus;
import studio.one.base.user.company.model.CompanyRole;
import studio.one.base.user.domain.entity.ApplicationCompanyMember;
import studio.one.base.user.domain.entity.ApplicationCompanyMemberId;
import studio.one.base.user.persistence.ApplicationCompanyMemberRepository;

@Repository(ApplicationCompanyMemberRepository.SERVICE_NAME)
public class ApplicationCompanyMemberJdbcRepository extends BaseJdbcRepository implements ApplicationCompanyMemberRepository {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "companyId", "COMPANY_ID",
            "userId", "USER_ID",
            "role", "ROLE",
            "status", "STATUS",
            "joinedAt", "JOINED_AT",
            "updatedAt", "UPDATED_AT");

    private static final RowMapper<ApplicationCompanyMember> MEMBER_ROW_MAPPER = (rs, rowNum) -> {
        ApplicationCompanyMember member = new ApplicationCompanyMember();
        member.setId(new ApplicationCompanyMemberId(rs.getLong("COMPANY_ID"), rs.getLong("USER_ID")));
        member.setRole(CompanyRole.valueOf(rs.getString("ROLE")));
        member.setStatus(CompanyMemberStatus.valueOf(rs.getString("STATUS")));
        Timestamp joinedAt = rs.getTimestamp("JOINED_AT");
        Timestamp updatedAt = rs.getTimestamp("UPDATED_AT");
        member.setJoinedAt(joinedAt == null ? null : joinedAt.toInstant());
        member.setUpdatedAt(updatedAt == null ? null : updatedAt.toInstant());
        long joinedBy = rs.getLong("JOINED_BY");
        member.setJoinedBy(rs.wasNull() ? null : joinedBy);
        long updatedBy = rs.getLong("UPDATED_BY");
        member.setUpdatedBy(rs.wasNull() ? null : updatedBy);
        return member;
    };

    public ApplicationCompanyMemberJdbcRepository(NamedParameterJdbcTemplate namedTemplate) {
        super(namedTemplate);
    }

    @Override
    public Page<ApplicationCompanyMember> findAllByCompanyId(Long companyId, Pageable pageable) {
        Map<String, Object> params = Map.of("companyId", companyId);
        String select = """
                select COMPANY_ID, USER_ID, ROLE, STATUS, JOINED_AT, JOINED_BY, UPDATED_AT, UPDATED_BY
                  from TB_APPLICATION_COMPANY_MEMBERS
                 where COMPANY_ID = :companyId
                """;
        String count = """
                select count(*)
                  from TB_APPLICATION_COMPANY_MEMBERS
                 where COMPANY_ID = :companyId
                """;
        return queryPage(select, count, params, pageable, MEMBER_ROW_MAPPER, "USER_ID", SORT_COLUMNS);
    }

    @Override
    public List<ApplicationCompanyMember> findAllByCompanyId(Long companyId) {
        String sql = """
                select COMPANY_ID, USER_ID, ROLE, STATUS, JOINED_AT, JOINED_BY, UPDATED_AT, UPDATED_BY
                  from TB_APPLICATION_COMPANY_MEMBERS
                 where COMPANY_ID = :companyId
                 order by USER_ID
                """;
        return namedTemplate.query(sql, Map.of("companyId", companyId), MEMBER_ROW_MAPPER);
    }

    @Override
    public Optional<ApplicationCompanyMember> findById(ApplicationCompanyMemberId id) {
        String sql = """
                select COMPANY_ID, USER_ID, ROLE, STATUS, JOINED_AT, JOINED_BY, UPDATED_AT, UPDATED_BY
                  from TB_APPLICATION_COMPANY_MEMBERS
                 where COMPANY_ID = :companyId
                   and USER_ID = :userId
                """;
        return queryOptional(sql, Map.of("companyId", id.getCompanyId(), "userId", id.getUserId()), MEMBER_ROW_MAPPER);
    }

    @Override
    public boolean existsById(ApplicationCompanyMemberId id) {
        String sql = """
                select exists(
                    select 1 from TB_APPLICATION_COMPANY_MEMBERS
                     where COMPANY_ID = :companyId
                       and USER_ID = :userId
                )
                """;
        Boolean exists = namedTemplate.queryForObject(
                sql,
                Map.of("companyId", id.getCompanyId(), "userId", id.getUserId()),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public ApplicationCompanyMember save(ApplicationCompanyMember member) {
        ApplicationCompanyMemberId id = member.getId();
        if (existsById(id)) {
            update(member);
        } else {
            insert(member);
        }
        return findById(id).orElse(member);
    }

    @Override
    public void deleteById(ApplicationCompanyMemberId id) {
        String sql = """
                delete from TB_APPLICATION_COMPANY_MEMBERS
                 where COMPANY_ID = :companyId
                   and USER_ID = :userId
                """;
        namedTemplate.update(sql, Map.of("companyId", id.getCompanyId(), "userId", id.getUserId()));
    }

    private void insert(ApplicationCompanyMember member) {
        Instant now = Instant.now();
        if (member.getJoinedAt() == null) {
            member.setJoinedAt(now);
        }
        if (member.getUpdatedAt() == null) {
            member.setUpdatedAt(member.getJoinedAt());
        }
        if (member.getStatus() == null) {
            member.setStatus(CompanyMemberStatus.ACTIVE);
        }
        String sql = """
                insert into TB_APPLICATION_COMPANY_MEMBERS
                    (COMPANY_ID, USER_ID, ROLE, STATUS, JOINED_AT, JOINED_BY, UPDATED_AT, UPDATED_BY)
                values
                    (:companyId, :userId, :role, :status, :joinedAt, :joinedBy, :updatedAt, :updatedBy)
                """;
        namedTemplate.update(sql, params(member));
    }

    private void update(ApplicationCompanyMember member) {
        member.setUpdatedAt(Instant.now());
        String sql = """
                update TB_APPLICATION_COMPANY_MEMBERS
                   set ROLE = :role,
                       STATUS = :status,
                       UPDATED_AT = :updatedAt,
                       UPDATED_BY = :updatedBy
                 where COMPANY_ID = :companyId
                   and USER_ID = :userId
                """;
        namedTemplate.update(sql, params(member));
    }

    private Map<String, Object> params(ApplicationCompanyMember member) {
        Map<String, Object> params = new HashMap<>();
        params.put("companyId", member.getId().getCompanyId());
        params.put("userId", member.getId().getUserId());
        params.put("role", member.getRole().name());
        params.put("status", member.getStatus().name());
        params.put("joinedAt", Timestamp.from(member.getJoinedAt()));
        params.put("joinedBy", member.getJoinedBy());
        params.put("updatedAt", Timestamp.from(member.getUpdatedAt()));
        params.put("updatedBy", member.getUpdatedBy());
        return params;
    }
}
