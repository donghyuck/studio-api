package studio.echo.base.user.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import studio.echo.base.user.domain.entity.ApplicationRole;

@Repository
public interface ApplicationRoleRepository extends JpaRepository<ApplicationRole, Long> {

    Optional<ApplicationRole> findByName(String name);

    // 사용자 직접 롤
    @Query("select r from ApplicationRole r " +
           "join ApplicationUserRole ur on ur.role = r " +
           "where ur.user.userId = :userId")
    List<ApplicationRole> findRolesByUserId(@Param("userId") Long userId);

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
    @Query("select distinct r from ApplicationRole r " +
           "join ApplicationGroupRole gr on gr.role = r " +
           "where gr.group.groupId in :groupIds")
    List<ApplicationRole> findRolesByGroupIds(@Param("groupIds") Collection<Long> groupIds);

    // 그룹 → 롤 (Page)
    @Query(
      value = "select r from ApplicationRole r " +
              "join ApplicationGroupRole gr on gr.role = r " +
              "where gr.group.groupId = :groupId",
      countQuery = "select count(r) from ApplicationRole r " +
                   "join ApplicationGroupRole gr on gr.role = r " +
                   "where gr.group.groupId = :groupId"
    )
    Page<ApplicationRole> findRolesByGroupId(@Param("groupId") Long groupId, Pageable pageable);

    // 효과적 권한 한 방 쿼리 (A안)
    @Query("select distinct r from ApplicationRole r " +
           "where r in (select ur.role from ApplicationUserRole ur where ur.user.userId = :userId) " +
           "   or r in (select gr.role from ApplicationGroupRole gr " +
           "            where gr.group in (select gm.group from ApplicationGroupMembership gm where gm.user.userId = :userId))")
    List<ApplicationRole> findEffectiveRolesByUserId(@Param("userId") Long userId);
}
