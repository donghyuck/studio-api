package studio.echo.base.user.domain.repository;

import java.util.List;
import java.util.Optional;

import javax.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import studio.echo.base.user.domain.entity.ApplicationGroup;
import studio.echo.base.user.domain.entity.ApplicationUser;
import studio.echo.base.user.domain.model.UserIdOnly;

@Repository
public interface ApplicationUserRepository extends JpaRepository<ApplicationUser, Long> {

        Optional<ApplicationUser> findByUsername(String username);

        boolean existsByUsername(String username);

        boolean existsByEmail(String email);

        // --- 그룹 기준 사용자 조회 ---
        @Query(
        value = "select u " +
                "from ApplicationGroupMembership gm " +
                "join gm.user u " +
                "where gm.group.groupId = :groupId",
        countQuery = "select count(u) " +
                "from ApplicationGroupMembership gm " +
                "join gm.user u " +
                "where gm.group.groupId = :groupId"
        )
        Page<ApplicationUser> findUsersByGroupId(@Param("groupId") Long groupId, Pageable pageable);

        @Query("select u from ApplicationUser u " +
                        "join ApplicationGroupMembership gm on gm.user = u " +
                        "where gm.group.groupId = :groupId")
        List<ApplicationUser> findUsersByGroupId(@Param("groupId") Long groupId);

        // --- 사용자 검색 (엔터티 Page) ---
        @Query(value = "select u from ApplicationUser u " +
                        "where (:q is null or :q = '' or " +
                        "      lower(u.username) like lower(concat('%', :q, '%')) or " +
                        "      lower(u.name) like lower(concat('%', :q, '%')) or " +
                        "      lower(u.email) like lower(concat('%', :q, '%')))", 
        countQuery = "select count(u) from ApplicationUser u "  +
                        "where (:q is null or :q = '' or " +
                        "      lower(u.username) like lower(concat('%', :q, '%')) or " +
                        "      lower(u.name) like lower(concat('%', :q, '%')) or " +
                        "      lower(u.email) like lower(concat('%', :q, '%')))")
        Page<ApplicationUser> search(@Param("q") String q, Pageable pageable);

        // --- 유틸: 사용자 → 그룹(엔터티/ID) ---
        @Query("select g from ApplicationGroup g join ApplicationGroupMembership gm on gm.group = g where gm.user.userId = :userId")
        List<ApplicationGroup> findGroupsByUserId(@Param("userId") Long userId);

        @Query("select gm.group.groupId from ApplicationGroupMembership gm where gm.user.userId = :userId")
        List<Long> findGroupIdsByUserId(@Param("userId") Long userId);

        @Query(value = "select u from ApplicationUser u where u.username=:username")
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        Optional<ApplicationUser> findByUsernameForUpdate(@Param("username") String username);

        Optional<UserIdOnly> findFirstByUsernameIgnoreCase(String username);
}