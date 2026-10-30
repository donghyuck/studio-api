package studio.one.base.security.jwt.reset.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import studio.one.base.security.jwt.reset.domain.model.PasswordResetToken;
import studio.one.base.security.jwt.reset.domain.port.PasswordResetTokenRepository;

/**
 * JPA-backed implementation of {@link PasswordResetTokenRepository}.
 */
public interface PasswordResetTokenJpaRepository
        extends JpaRepository<PasswordResetToken, Long>, PasswordResetTokenRepository {

    @Override
    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findFirstByUserIdAndUsedFalseOrderByCreatedAtDescIdDesc(Long userId);

    Optional<PasswordResetToken> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordResetToken token
               set token.used = true
             where token.id = :id
               and token.userId = :userId
               and token.used = false
               and not exists (
                   select newer.id
                     from PasswordResetToken newer
                    where newer.userId = :userId
                      and (
                          newer.createdAt > :createdAt
                          or (newer.createdAt = :createdAt and newer.id > :id)
                      )
               )
            """)
    int markLatestUnusedClaimed(@Param("id") Long id, @Param("userId") Long userId,
            @Param("createdAt") Instant createdAt);

    @Override
    default Optional<PasswordResetToken> findActiveByUserId(Long userId) {
        return findFirstByUserIdAndUsedFalseOrderByCreatedAtDescIdDesc(userId);
    }

    @Override
    default Optional<PasswordResetToken> findLatestByUserId(Long userId) {
        return findFirstByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    @Override
    default boolean claimLatestUnused(Long id, Long userId, Instant createdAt) {
        return markLatestUnusedClaimed(id, userId, createdAt) == 1;
    }

}
