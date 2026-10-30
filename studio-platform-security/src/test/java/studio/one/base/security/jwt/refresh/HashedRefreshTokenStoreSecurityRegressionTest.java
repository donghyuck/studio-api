package studio.one.base.security.jwt.refresh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import studio.one.base.security.jwt.JwtConfig;
import studio.one.base.security.jwt.refresh.domain.model.RefreshToken;
import studio.one.base.security.jwt.refresh.domain.port.RefreshTokenRepository;

class HashedRefreshTokenStoreSecurityRegressionTest {

    private static final Instant NOW = Instant.parse("2030-02-01T00:00:00Z");
    private static final Duration REFRESH_TTL = Duration.ofMinutes(10);
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(30);

    @Test
    void rotateRejectsReplayAndInvalidVerifierWhileKeepingNormalFlow() {
        InMemoryRefreshTokenRepository repository = new InMemoryRefreshTokenRepository();
        RefreshTokenStore store = new HashedRefreshTokenStore(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new TestJwtConfig());

        String raw = store.mint(42L, NOW.plus(Duration.ofHours(1)));
        String invalidVerifier = selector(raw) + ".wrong-verifier";

        assertEquals(42L, store.resolveUserId(raw));
        assertThrows(IllegalArgumentException.class, () -> store.rotate(invalidVerifier));
        assertFalse(repository.bySelector(selector(raw)).isRevoked());

        String successorRaw = store.rotate(raw);

        assertNotEquals(selector(raw), selector(successorRaw));
        assertEquals(42L, store.resolveUserId(successorRaw));
        assertThrows(IllegalStateException.class, () -> store.rotate(raw));
        RefreshToken predecessor = repository.bySelector(selector(raw));
        RefreshToken successor = repository.bySelector(selector(successorRaw));
        assertTrue(predecessor.isRevoked());
        assertNotNull(predecessor.getReplacedBy());
        assertEquals(successor.getId(), predecessor.getReplacedBy().getId());
        assertFalse(successor.isRevoked());
    }

    @Test
    void rotatePreservesRefreshTtlAndRotationGrace() {
        InMemoryRefreshTokenRepository repository = new InMemoryRefreshTokenRepository();
        RefreshTokenStore store = new HashedRefreshTokenStore(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new TestJwtConfig());

        String graceRaw = store.mint(84L, NOW.minusSeconds(10));
        String graceSuccessorRaw = store.rotate(graceRaw);
        RefreshToken graceSuccessor = repository.bySelector(selector(graceSuccessorRaw));
        assertEquals(NOW.plus(REFRESH_TTL), graceSuccessor.getExpiresAt());

        String expiredRaw = store.mint(84L, NOW.minus(ROTATION_GRACE).minusSeconds(1));
        assertThrows(IllegalStateException.class, () -> store.rotate(expiredRaw));
        assertFalse(repository.bySelector(selector(expiredRaw)).isRevoked());
    }

    private static String selector(String raw) {
        return raw.substring(0, raw.indexOf('.'));
    }

    private static final class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

        private final AtomicLong ids = new AtomicLong();
        private final Map<Long, RefreshToken> byId = new LinkedHashMap<>();
        private final Map<String, Long> idsBySelector = new LinkedHashMap<>();

        @Override
        public RefreshToken save(RefreshToken token) {
            if (token.getId() == null) {
                token.setId(ids.incrementAndGet());
            }
            RefreshToken copy = copyOf(token);
            byId.put(copy.getId(), copy);
            idsBySelector.put(copy.getSelector(), copy.getId());
            token.setId(copy.getId());
            return token;
        }

        @Override
        public Optional<RefreshToken> findBySelector(String selector) {
            Long id = idsBySelector.get(selector);
            if (id == null) {
                return Optional.empty();
            }
            return Optional.of(copyOf(byId.get(id)));
        }

        @Override
        public int revokeIfActive(Long id, String selector) {
            RefreshToken token = byId.get(id);
            if (token == null || token.isRevoked() || !selector.equals(token.getSelector())) {
                return 0;
            }
            token.setRevoked(true);
            return 1;
        }

        @Override
        public int linkReplacement(Long predecessorId, String predecessorSelector, RefreshToken successor) {
            RefreshToken predecessor = byId.get(predecessorId);
            RefreshToken storedSuccessor = byId.get(successor.getId());
            if (predecessor == null
                    || storedSuccessor == null
                    || !predecessorSelector.equals(predecessor.getSelector())
                    || !predecessor.isRevoked()
                    || predecessor.getReplacedBy() != null) {
                return 0;
            }
            predecessor.setReplacedBy(RefreshToken.builder().id(storedSuccessor.getId()).build());
            return 1;
        }

        RefreshToken bySelector(String selector) {
            return byId.get(idsBySelector.get(selector));
        }

        private RefreshToken copyOf(RefreshToken token) {
            return RefreshToken.builder()
                    .id(token.getId())
                    .userId(token.getUserId())
                    .selector(token.getSelector())
                    .verifierHash(token.getVerifierHash())
                    .expiresAt(token.getExpiresAt())
                    .revoked(token.isRevoked())
                    .createdAt(token.getCreatedAt())
                    .replacedBy(token.getReplacedBy() == null
                            ? null
                            : RefreshToken.builder().id(token.getReplacedBy().getId()).build())
                    .build();
        }
    }

    private static final class TestJwtConfig implements JwtConfig {

        @Override
        public Duration getAccessTtl() {
            return Duration.ofMinutes(15);
        }

        @Override
        public Duration getRefreshTtl() {
            return REFRESH_TTL;
        }

        @Override
        public Duration getRotationGrace() {
            return ROTATION_GRACE;
        }

        @Override
        public String getHeader() {
            return "Authorization";
        }

        @Override
        public String getPrefix() {
            return "Bearer";
        }

        @Override
        public String getClaimAuthorities() {
            return "authorities";
        }

        @Override
        public String getRefreshCookieName() {
            return "refresh_token";
        }

        @Override
        public String getCookiePath() {
            return "/";
        }

        @Override
        public boolean isCookieSecure() {
            return true;
        }

        @Override
        public String getCookieSameSite() {
            return "Strict";
        }

        @Override
        public String getSecret() {
            return "01234567890123456789012345678901";
        }

        @Override
        public String getIssuer() {
            return "studio-test";
        }
    }
}
