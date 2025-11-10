package studio.one.base.user.persistence.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.entity.ApplicationRole;
import studio.one.base.user.persistence.ApplicationRoleRepository;

@Repository
public interface ApplicationRoleJpaRepository extends JpaRepository<ApplicationRole, Long>, ApplicationRoleRepository {

    @Override
    Optional<ApplicationRole> findByName(String name);

    @Override
    boolean existsByName(String name);

    // 사용자 직접 롤
    @Override
    @Query("select r from ApplicationRole r " +
           "join ApplicationUserRole ur on ur.role = r " +
           "where ur.user.userId = :userId")
    List<ApplicationRole> findRolesByUserId(@Param("userId") Long userId);

    @Override
    @Query(
      value = "select r from ApplicationRole r " +
              "join ApplicationUserRole ur on ur.role = r " +
              "where ur.user.userId = :userId",
      countQuery = "select count(r) from ApplicationRole r " +
                   "join ApplicationUserRole ur on ur.role = r " +
                   "where ur.user.userId = :userId"
    )
    Page<ApplicationRole> findRolesByUserId(@Param("userId") Long userId, Pageable pageable);

    // 여러 그룹의 롤 한 번에 (N+1 제거)
    @Override
    @Query("select distinct r from ApplicationRole r " +
           "join ApplicationGroupRole gr on gr.role = r " +
           "where gr.group.groupId in :groupIds")
    List<ApplicationRole> findRolesByGroupIds(@Param("groupIds") Collection<Long> groupIds);

    // 그룹 → 롤 (Page)
    @Override
    @Query(
      value = "select r from ApplicationRole r " +
              "join ApplicationGroupRole gr on gr.role = r " +
              "where gr.group.groupId = :groupId",
      countQuery = "select count(r) from ApplicationRole r " +
                   "join ApplicationGroupRole gr on gr.role = r " +
                   "where gr.group.groupId = :groupId"
    )
    Page<ApplicationRole> findRolesByGroupId(@Param("groupId") Long groupId, Pageable pageable);

    @Override
    @Query("select r from ApplicationRole r join ApplicationGroupRole gr on gr.role = r " +
         "where gr.group.groupId = :groupId")
    List<ApplicationRole> findRolesByGroupId(@Param("groupId") Long groupId, Sort sort);

    // 사용자가 속한 그룹과 사용자에게 부여된 권한을 한번에 조회
    @Override
    @Query("select distinct r from ApplicationRole r " +
           "where r in (select ur.role from ApplicationUserRole ur where ur.user.userId = :userId) " +
           "   or r in (select gr.role from ApplicationGroupRole gr " +
           "            where gr.group in (select gm.group from ApplicationGroupMembership gm where gm.user.userId = :userId))")
    List<ApplicationRole> findEffectiveRolesByUserId(@Param("userId") Long userId);

    /* ======================================================================
     * 배치 검증용: 존재하는 roleId 추출
     *    (존재하지 않는 ID를 걸러내어 insert 시 무결성 오류 방지)
     * ====================================================================== */
    @Override
    @Query("select r.roleId from ApplicationRole r where r.roleId in :ids")
    List<Long> findExistingIds(@Param("ids") Collection<Long> ids);
}
