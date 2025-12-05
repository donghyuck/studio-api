package studio.one.base.security.jwt.reset.persistence.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.base.security.jwt.reset.domain.PasswordResetToken;
import studio.one.base.security.jwt.reset.persistence.PasswordResetTokenRepository;

/**
 * JPA-backed implementation of {@link PasswordResetTokenRepository}.
 */
public interface PasswordResetTokenJpaRepository
        extends JpaRepository<PasswordResetToken, Long>, PasswordResetTokenRepository {

    @Override
    PasswordResetToken save(PasswordResetToken token);

    @Override
    Optional<PasswordResetToken> findByToken(String token);

}
