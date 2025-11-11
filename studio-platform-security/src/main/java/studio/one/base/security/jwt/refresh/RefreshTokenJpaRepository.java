package studio.one.base.security.jwt.refresh;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA-backed implementation of {@link RefreshTokenRepository}.
 */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long>, RefreshTokenRepository {

    @Override
    RefreshToken save(RefreshToken token);

    @Override
    java.util.Optional<RefreshToken> findBySelector(String selector);
}
