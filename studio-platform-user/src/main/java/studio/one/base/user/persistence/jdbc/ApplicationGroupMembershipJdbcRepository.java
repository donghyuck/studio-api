package studio.one.base.user.persistence.jdbc;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.entity.ApplicationGroup;
import studio.one.base.user.domain.entity.ApplicationGroupMemberSummary;
import studio.one.base.user.domain.entity.ApplicationGroupMembership;
import studio.one.base.user.domain.entity.ApplicationGroupMembershipId;
import studio.one.base.user.persistence.ApplicationGroupMembershipRepository;

@Repository(ApplicationGroupMembershipRepository.SERVICE_NAME)
public class ApplicationGroupMembershipJdbcRepository extends BaseJdbcRepository
        implements ApplicationGroupMembershipRepository {

    private static final RowMapper<ApplicationGroupMembership> MEMBERSHIP_ROW_MAPPER = (rs, rowNum) -> {
        ApplicationGroupMembership membership = new ApplicationGroupMembership();
        ApplicationGroupMembershipId id = new ApplicationGroupMembershipId(rs.getLong("GROUP_ID"), rs.getLong("USER_ID"));
        membership.setId(id);

        ApplicationGroup group = new ApplicationGroup();
        group.setGroupId(id.getGroupId());
        membership.setGroup(group);

        Timestamp joinedAt = rs.getTimestamp("JOINED_AT");
        membership.setJoinedAt(joinedAt == null ? LocalDateTime.now() : joinedAt.toLocalDateTime());
        membership.setJoinedBy(rs.getString("JOINED_BY"));
        return membership;
    };

    private static final RowMapper<ApplicationGroupMemberSummary> GROUP_MEMBER_SUMMARY_ROW_MAPPER = (rs, rowNum) ->
            new JdbcApplicationGroupMemberSummary(
                    rs.getLong("USER_ID"),
                    rs.getString("USERNAME"),
                    rs.getString("NAME"),
                    rs.getBoolean("ENABLED"));

    public ApplicationGroupMembershipJdbcRepository(NamedParameterJdbcTemplate namedTemplate) {
        super(namedTemplate);
    }

    @Override
    public Page<ApplicationGroupMembership> findAll(Pageable pageable) {
        String select = "select GROUP_ID, USER_ID, JOINED_AT, JOINED_BY from TB_APPLICATION_GROUP_MEMBERS";
        String count = "select count(*) from TB_APPLICATION_GROUP_MEMBERS";
        return queryPage(select, count, Map.of(), pageable, MEMBERSHIP_ROW_MAPPER, "GROUP_ID", Map.of());
    }

    @Override
    public Page<ApplicationGroupMembership> findAllByGroupId(Long groupId, Pageable pageable) {
        Map<String, Object> params = Map.of("groupId", groupId);
        String select = """
                select GROUP_ID, USER_ID, JOINED_AT, JOINED_BY
                  from TB_APPLICATION_GROUP_MEMBERS
                 where GROUP_ID = :groupId
                """;
        String count = "select count(*) from TB_APPLICATION_GROUP_MEMBERS where GROUP_ID = :groupId";
        return queryPage(select, count, params, pageable, MEMBERSHIP_ROW_MAPPER, "USER_ID", Map.of());
    }

    @Override
    public Page<Long> findUserIdsByGroupId(Long groupId, Pageable pageable) {
        Map<String, Object> params = Map.of("groupId", groupId);
        String select = """
                select USER_ID
                  from TB_APPLICATION_GROUP_MEMBERS
                 where GROUP_ID = :groupId
                """;
        String count = "select count(*) from TB_APPLICATION_GROUP_MEMBERS where GROUP_ID = :groupId";
        return queryPage(select, count, params, pageable, (rs, rowNum) -> rs.getLong("USER_ID"), "USER_ID",
                Map.of());
    }

    @Override
    public Page<ApplicationGroupMemberSummary> findMemberSummariesByGroupId(Long groupId, @Nullable String keyword,
            Pageable pageable) {
        Map<String, Object> params = Map.of("groupId", groupId, "q", normalize(keyword));
        String select = """
                select u.USER_ID, u.USERNAME, u.NAME, u.ENABLED
                  from TB_APPLICATION_GROUP_MEMBERS gm
                  join TB_APPLICATION_USER u on u.USER_ID = gm.USER_ID
                 where gm.GROUP_ID = :groupId
                   and (:q = '' or lower(u.USERNAME) like :q or lower(u.NAME) like :q or lower(u.EMAIL) like :q)
                """;
        String count = """
                select count(*)
                  from TB_APPLICATION_GROUP_MEMBERS gm
                  join TB_APPLICATION_USER u on u.USER_ID = gm.USER_ID
                 where gm.GROUP_ID = :groupId
                   and (:q = '' or lower(u.USERNAME) like :q or lower(u.NAME) like :q or lower(u.EMAIL) like :q)
                """;
        return queryPage(select, count, params, pageable, GROUP_MEMBER_SUMMARY_ROW_MAPPER, "u.USER_ID", Map.of(
                "userId", "u.USER_ID",
                "username", "u.USERNAME",
                "name", "u.NAME",
                "enabled", "u.ENABLED"));
    }

    @Override
    public Page<ApplicationGroupMembership> findAllByUserId(Long userId, Pageable pageable) {
        Map<String, Object> params = Map.of("userId", userId);
        String select = """
                select GROUP_ID, USER_ID, JOINED_AT, JOINED_BY
                  from TB_APPLICATION_GROUP_MEMBERS
                 where USER_ID = :userId
                """;
        String count = "select count(*) from TB_APPLICATION_GROUP_MEMBERS where USER_ID = :userId";
        return queryPage(select, count, params, pageable, MEMBERSHIP_ROW_MAPPER, "GROUP_ID", Map.of());
    }

    @Override
    public List<GroupCount> countMembersByGroupIds(Collection<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                select GROUP_ID, count(*) as CNT
                  from TB_APPLICATION_GROUP_MEMBERS
                 where GROUP_ID in (:groupIds)
                 group by GROUP_ID
                """;
        return namedTemplate.query(sql, Map.of("groupIds", groupIds),
                (rs, rowNum) -> new JdbcGroupCount(rs.getLong("GROUP_ID"), rs.getLong("CNT")));
    }

    @Override
    public int deleteByGroupIdAndUserIds(Long groupId, Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        String sql = """
                delete from TB_APPLICATION_GROUP_MEMBERS
                 where GROUP_ID = :groupId
                   and USER_ID in (:userIds)
                """;
        return namedTemplate.update(sql, Map.of("groupId", groupId, "userIds", userIds));
    }

    @Override
    public List<Long> findExistingUserIdsInGroup(Long groupId, Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                select USER_ID
                  from TB_APPLICATION_GROUP_MEMBERS
                 where GROUP_ID = :groupId
                   and USER_ID in (:userIds)
                """;
        return namedTemplate.query(sql, Map.of("groupId", groupId, "userIds", userIds),
                (rs, rowNum) -> rs.getLong("USER_ID"));
    }

    @Override
    public int insertIgnoreConflicts(Long groupId, Long[] userIds, LocalDateTime joinedAt, String joinedBy) {
        if (userIds == null || userIds.length == 0) {
            return 0;
        }
        String sql = """
                insert into TB_APPLICATION_GROUP_MEMBERS (GROUP_ID, USER_ID, JOINED_AT, JOINED_BY)
                values (:groupId, :userId, :joinedAt, :joinedBy)
                on conflict (GROUP_ID, USER_ID) do nothing
                """;
        LocalDateTime actualJoinedAt = joinedAt == null ? LocalDateTime.now() : joinedAt;
        Timestamp ts = Timestamp.valueOf(actualJoinedAt);

        SqlParameterSource[] batch = new SqlParameterSource[userIds.length];
        for (int i = 0; i < userIds.length; i++) {
            batch[i] = new MapSqlParameterSource()
                    .addValue("groupId", groupId)
                    .addValue("userId", userIds[i])
                    .addValue("joinedAt", ts)
                    .addValue("joinedBy", joinedBy);
        }
        int[] result = namedTemplate.batchUpdate(sql, batch);
        int affected = 0;
        for (int value : result) {
            affected += value;
        }
        return affected;
    }

    @Override
    public boolean existsById(ApplicationGroupMembershipId id) {
        if (id == null) {
            return false;
        }
        String sql = """
                select exists(
                    select 1 from TB_APPLICATION_GROUP_MEMBERS
                     where GROUP_ID = :groupId
                       and USER_ID = :userId)
                """;
        Boolean exists = namedTemplate.queryForObject(sql, Map.of(
                "groupId", id.getGroupId(),
                "userId", id.getUserId()), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public ApplicationGroupMembership save(ApplicationGroupMembership membership) {
        ApplicationGroupMembershipId id = membership.getId();
        if (id == null) {
            throw new IllegalArgumentException("membership id must not be null");
        }
        String sql = """
                insert into TB_APPLICATION_GROUP_MEMBERS (GROUP_ID, USER_ID, JOINED_AT, JOINED_BY)
                values (:groupId, :userId, :joinedAt, :joinedBy)
                on conflict (GROUP_ID, USER_ID) do update
                      set JOINED_AT = excluded.JOINED_AT,
                          JOINED_BY = excluded.JOINED_BY
                """;
        LocalDateTime joinedAt = membership.getJoinedAt() == null ? LocalDateTime.now() : membership.getJoinedAt();
        namedTemplate.update(sql, Map.of(
                "groupId", id.getGroupId(),
                "userId", id.getUserId(),
                "joinedAt", Timestamp.valueOf(joinedAt),
                "joinedBy", membership.getJoinedBy()));
        membership.setJoinedAt(joinedAt);
        return membership;
    }

    @Override
    public <S extends ApplicationGroupMembership> List<S> saveAll(Iterable<S> memberships) {
        List<S> buffer = new ArrayList<>();
        memberships.forEach(buffer::add);
        if (buffer.isEmpty()) {
            return buffer;
        }
        String sql = """
                insert into TB_APPLICATION_GROUP_MEMBERS (GROUP_ID, USER_ID, JOINED_AT, JOINED_BY)
                values (:groupId, :userId, :joinedAt, :joinedBy)
                on conflict (GROUP_ID, USER_ID) do update
                      set JOINED_AT = excluded.JOINED_AT,
                          JOINED_BY = excluded.JOINED_BY
                """;
        SqlParameterSource[] batch = buffer.stream()
                .map(m -> {
                    ApplicationGroupMembershipId id = m.getId();
                    LocalDateTime joinedAt = m.getJoinedAt() == null ? LocalDateTime.now() : m.getJoinedAt();
                    m.setJoinedAt(joinedAt);
                    return new MapSqlParameterSource()
                            .addValue("groupId", id.getGroupId())
                            .addValue("userId", id.getUserId())
                            .addValue("joinedAt", Timestamp.valueOf(joinedAt))
                            .addValue("joinedBy", m.getJoinedBy());
                })
                .toArray(SqlParameterSource[]::new);
        namedTemplate.batchUpdate(sql, batch);
        return buffer;
    }

    @Override
    public void deleteById(ApplicationGroupMembershipId id) {
        if (id == null) {
            return;
        }
        String sql = """
                delete from TB_APPLICATION_GROUP_MEMBERS
                 where GROUP_ID = :groupId
                   and USER_ID = :userId
                """;
        namedTemplate.update(sql, Map.of("groupId", id.getGroupId(), "userId", id.getUserId()));
    }

    private static String normalize(@Nullable String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        return "%" + keyword.trim().toLowerCase() + "%";
    }

    private record JdbcGroupCount(Long groupId, long count) implements GroupCount {
        @Override
        public Long getGroupId() {
            return groupId;
        }

        @Override
        public long getCount() {
            return count;
        }
    }

    private record JdbcApplicationGroupMemberSummary(Long userId, String username, String name, boolean enabled)
            implements ApplicationGroupMemberSummary {
        @Override
        public Long getUserId() {
            return userId;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }
}
