package studio.one.base.user.domain.repository;

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

@Repository
public interface ApplicationGroupRoleRepository extends JpaRepository<ApplicationGroupRole, ApplicationGroupRoleId> {

    /**
     * 특정 그룹에 속한 모든 롤 조회
     */
    @Query("SELECT gr.role FROM ApplicationGroupRole gr WHERE gr.group.groupId = :groupId")
    List<ApplicationRole> findRolesByGroupId(@Param("groupId") Long groupId);

    /**
     * 특정 롤을 가진 모든 그룹 조회
     */
    @Query("SELECT gr.group FROM ApplicationGroupRole gr WHERE gr.role.roleId = :roleId")
    List<ApplicationGroup> findGroupsByRoleId(@Param("roleId") Long roleId);

    /**
     * 특정 그룹에 특정 롤이 존재하는지 여부 확인
     */
    boolean existsByGroup_GroupIdAndRole_RoleId(Long groupId, Long roleId);

    /**
     * 특정 그룹의 롤 전체 삭제
     */
    void deleteByGroup_GroupId(Long groupId);

    /**
     * 특정 롤이 연결된 그룹 관계 전체 삭제
     */
    void deleteByRole_RoleId(Long roleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ApplicationGroupRole gr where gr.group.groupId = :groupId and gr.role.roleId in :roleIds")
    int deleteByGroupIdAndRoleIds(@Param("groupId") Long groupId, @Param("roleIds") Collection<Long> roleIds);


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ApplicationGroupRole gr where gr.group.groupId in :groupIds and gr.role.roleId = :roleId")
    int deleteByGroupIdsAndRoleId(@Param("groupIds") Collection<Long> groupIds, @Param("roleId") Long roleId ) ;

    /**
     * 특정 롤에 해당하는 그룹 조회
     */
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
