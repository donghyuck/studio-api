package studio.one.base.user.persistence.jpa;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.entity.ApplicationUser;
import studio.one.base.user.persistence.AccountLockRepository;

@Repository
public interface AccountLockJpaRepository extends JpaRepository<ApplicationUser, Long>, AccountLockRepository {

    /**
     * 실패 1회 증가 + 마지막 실패 시각 업데이트 (원자적 증가)
     */
    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ApplicationUser u
        set u.failedAttempts = u.failedAttempts + 1, 
        u.lastFailedAt = :now
        where u.username = :username
        """)
    int bumpFailedAttempts(@Param("username") String username, @Param("now") Instant now);

    /**
     * 잠금 시각(까지) 설정. until == null 이면 해제와 동일.
     */
    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ApplicationUser u 
        set u.accountLockedUntil = :until
        where u.username = :username
        """)
    int lockUntil(@Param("username") String username, @Param("until") Instant until);

    /**
     * 성공 로그인 시 실패 이력/잠금 해제
     */
    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ApplicationUser u
        set u.failedAttempts = 0,
        u.lastFailedAt = null,
        u.accountLockedUntil = null
        where u.username = :username
        """)
    int resetLockState(@Param("username") String username);

    // ── 읽기용 경량 쿼리들 ─────────────────────────────────────────────
    @Override
    @Query("select u.failedAttempts from ApplicationUser u where u.username = :username")
    Integer findFailedAttempts(@Param("username") String username);

    @Override
    @Query("select u.lastFailedAt from ApplicationUser u where u.username = :username")
    Instant findLastFailedAt(@Param("username") String username);

    @Override
    @Query("select u.accountLockedUntil from ApplicationUser u where u.username = :username")
    Instant findAccountLockedUntil(@Param("username") String username);
}
