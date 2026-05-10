package studio.one.base.security.jwt.refresh.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.base.security.jwt.refresh.domain.model.RefreshToken;
import studio.one.base.security.jwt.refresh.domain.port.RefreshTokenRepository;

/**
 * JPA-backed implementation of {@link RefreshTokenRepository}.
 */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long>, RefreshTokenRepository {

    @Override
    RefreshToken save(RefreshToken token);

    @Override
    java.util.Optional<RefreshToken> findBySelector(String selector);
}
