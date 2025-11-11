package studio.one.base.security.authentication.lock;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import studio.one.base.user.domain.entity.ApplicationUser;

public interface AccountLockJpaRepository extends JpaRepository<ApplicationUser, Long>, AccountLockRepository {

    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ApplicationUser u
           set u.failedAttempts = u.failedAttempts + 1,
               u.lastFailedAt = :now
         where u.username = :username
        """)
    int bumpFailedAttempts(@Param("username") String username, @Param("now") Instant now);

    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ApplicationUser u
           set u.accountLockedUntil = :until
         where u.username = :username
        """)
    int lockUntil(@Param("username") String username, @Param("until") Instant until);

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
