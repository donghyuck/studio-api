package studio.one.base.security.jwt.reset.infrastructure.persistence.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.base.security.jwt.reset.domain.model.PasswordResetToken;
import studio.one.base.security.jwt.reset.domain.port.PasswordResetTokenRepository;

/**
 * JPA-backed implementation of {@link PasswordResetTokenRepository}.
 */
public interface PasswordResetTokenJpaRepository
        extends JpaRepository<PasswordResetToken, Long>, PasswordResetTokenRepository {

    @Override
    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(Long userId);

    @Override
    default Optional<PasswordResetToken> findActiveByUserId(Long userId) {
        return findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(userId);
    }

}
