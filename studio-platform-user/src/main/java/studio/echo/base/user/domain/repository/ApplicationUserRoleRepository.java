package studio.echo.base.user.domain.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import studio.echo.base.user.domain.entity.ApplicationRole;
import studio.echo.base.user.domain.entity.ApplicationUser;
import studio.echo.base.user.domain.entity.ApplicationUserRole;
import studio.echo.base.user.domain.entity.ApplicationUserRoleId;

@Repository
public interface ApplicationUserRoleRepository extends JpaRepository<ApplicationUserRole, ApplicationUserRoleId> {

    // ---------- 기본 탐색 ----------
    List<ApplicationUserRole> findAllByUser_UserId(Long userId);
    Page<ApplicationUserRole> findAllByUser_UserId(Long userId, Pageable pageable);

    List<ApplicationUserRole> findAllByRole_RoleId(Long roleId);
    Page<ApplicationUserRole> findAllByRole_RoleId(Long roleId, Pageable pageable);

    boolean existsByUser_UserIdAndRole_RoleId(Long userId, Long roleId);

    @Modifying
    @Query("delete from ApplicationUserRole ur where ur.user.userId = :userId and ur.role.roleId = :roleId")
    int deleteByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);

    // ---------- 사용자 → 역할 목록 ----------
    @Query(
        value =
            "select r from ApplicationRole r " +
            " join ApplicationUserRole ur on ur.role = r " +
            " where ur.user.userId = :userId",
        countQuery =
            "select count(r) from ApplicationRole r " +
            " join ApplicationUserRole ur on ur.role = r " +
            " where ur.user.userId = :userId"
    )
    Page<ApplicationRole> findRolesByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query(
        "select r from ApplicationRole r " +
        " join ApplicationUserRole ur on ur.role = r " +
        " where ur.user.userId = :userId"
    )
    List<ApplicationRole> findRolesByUserId(@Param("userId") Long userId);

    // 필요한 경우: ID만 빠르게
    @Query("select ur.role.roleId from ApplicationUserRole ur where ur.user.userId = :userId")
    List<Long> findRoleIdsByUserId(@Param("userId") Long userId);

    // ---------- 역할 → 사용자 목록 ----------
    @Query(
        value =
            "select u from ApplicationUser u " +
            " join ApplicationUserRole ur on ur.user = u " +
            " where ur.role.roleId = :roleId",
        countQuery =
            "select count(u) from ApplicationUser u " +
            " join ApplicationUserRole ur on ur.user = u " +
            " where ur.role.roleId = :roleId"
    )
    Page<ApplicationUser> findUsersByRoleId(@Param("roleId") Long roleId, Pageable pageable);

    @Query(
        "select u from ApplicationUser u " +
        " join ApplicationUserRole ur on ur.user = u " +
        " where ur.role.roleId = :roleId"
    )
    List<ApplicationUser> findUsersByRoleId(@Param("roleId") Long roleId);
}
