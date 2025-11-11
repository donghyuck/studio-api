package studio.one.base.security.jwt.refresh.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.base.security.jwt.refresh.domain.entity.RefreshToken;
import studio.one.base.security.jwt.refresh.persistence.RefreshTokenRepository;

/**
 * JPA-backed implementation of {@link RefreshTokenRepository}.
 */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long>, RefreshTokenRepository {

    @Override
    RefreshToken save(RefreshToken token);

    @Override
    java.util.Optional<RefreshToken> findBySelector(String selector);
}
