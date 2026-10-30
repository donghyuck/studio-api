package studio.one.base.security.jwt.refresh.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken token
               set token.revoked = true
             where token.id = :id
               and token.selector = :selector
               and token.revoked = false
            """)
    int revokeIfActive(@Param("id") Long id, @Param("selector") String selector);

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken token
               set token.replacedBy = :successor
             where token.id = :predecessorId
               and token.selector = :predecessorSelector
               and token.revoked = true
               and token.replacedBy is null
            """)
    int linkReplacement(
            @Param("predecessorId") Long predecessorId,
            @Param("predecessorSelector") String predecessorSelector,
            @Param("successor") RefreshToken successor);
}
