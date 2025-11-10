package studio.one.base.user.persistence.jpa;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.entity.ApplicationGroup;
import studio.one.base.user.domain.entity.ApplicationGroupRole;
import studio.one.base.user.domain.entity.ApplicationGroupRoleId;
import studio.one.base.user.domain.entity.ApplicationGroupWithMemberCount;
import studio.one.base.user.domain.entity.ApplicationRole;
import studio.one.base.user.persistence.ApplicationGroupRoleRepository;

@Repository
public interface ApplicationGroupRoleJpaRepository extends JpaRepository<ApplicationGroupRole, ApplicationGroupRoleId>,
    ApplicationGroupRoleRepository {

    @Override
    @Query("SELECT gr.role FROM ApplicationGroupRole gr WHERE gr.group.groupId = :groupId")
    List<ApplicationRole> findRolesByGroupId(@Param("groupId") Long groupId);

    @Override
    @Query("SELECT gr.group FROM ApplicationGroupRole gr WHERE gr.role.roleId = :roleId")
    List<ApplicationGroup> findGroupsByRoleId(@Param("roleId") Long roleId);

    boolean existsByGroup_GroupIdAndRole_RoleId(Long groupId, Long roleId);

    @Override
    default boolean existsByGroupIdAndRoleId(Long groupId, Long roleId) {
        return existsByGroup_GroupIdAndRole_RoleId(groupId, roleId);
    }

    void deleteByGroup_GroupId(Long groupId);

    @Override
    default void deleteByGroupId(Long groupId) {
        deleteByGroup_GroupId(groupId);
    }

    void deleteByRole_RoleId(Long roleId);

    @Override
    default void deleteByRoleId(Long roleId) {
        deleteByRole_RoleId(roleId);
    }

    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ApplicationGroupRole gr where gr.group.groupId = :groupId and gr.role.roleId in :roleIds")
    int deleteByGroupIdAndRoleIds(@Param("groupId") Long groupId, @Param("roleIds") Collection<Long> roleIds);

    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ApplicationGroupRole gr where gr.group.groupId in :groupIds and gr.role.roleId = :roleId")
    int deleteByGroupIdsAndRoleId(@Param("groupIds") Collection<Long> groupIds, @Param("roleId") Long roleId);

    @Override
    @Query("""
    select g
      from ApplicationGroup g
      join ApplicationGroupRole gr on gr.group = g
     where gr.role.roleId = :roleId
       and (
            :q is null
         or lower(g.name) like lower(concat('%', :q, '%'))
         or lower(g.description) like lower(concat('%', :q, '%'))
       )
    """)
    Page<ApplicationGroup> findGroupsByRoleId(
            @Param("roleId") Long roleId,
            @Param("q") String q,
            Pageable pageable);

    @Override
    @Query(
      value = """
        select distinct g as entity,
              (select count(m)
                  from ApplicationGroupMembership m
                where m.group = g) as memberCount
          from ApplicationGroup g
          join ApplicationGroupRole gr on gr.group = g
        where gr.role.roleId = :roleId
          and (
                :q is null
            or lower(g.name) like lower(concat('%', :q, '%'))
            or lower(g.description) like lower(concat('%', :q, '%'))
          )
        """,
      countQuery = """
        select count(distinct g)
          from ApplicationGroup g
          join ApplicationGroupRole gr on gr.group = g
        where gr.role.roleId = :roleId
          and (
                :q is null
            or lower(g.name) like lower(concat('%', :q, '%'))
            or lower(g.description) like lower(concat('%', :q, '%'))
          )
        """
    )
    Page<ApplicationGroupWithMemberCount> findGroupsWithMemberCountByRoleId(
            @Param("roleId") Long roleId,
            @Param("q") String q,
            Pageable pageable);
}
