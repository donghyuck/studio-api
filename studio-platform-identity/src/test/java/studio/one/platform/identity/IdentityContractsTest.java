package studio.one.platform.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class IdentityContractsTest {

    @Test
    void resolveDispatchesUserIdKeyToFindById() {
        RecordingIdentityService service = new RecordingIdentityService();

        Optional<UserRef> resolved = service.resolve(new UserIdKey(7L));

        assertTrue(resolved.isPresent());
        assertEquals(new UserRef(7L, "id-user", Set.of("ADMIN")), resolved.get());
        assertEquals(7L, service.lastResolvedId);
        assertNull(service.lastResolvedUsername);
    }

    @Test
    void resolveDispatchesUsernameKeyToFindByUsername() {
        RecordingIdentityService service = new RecordingIdentityService();

        Optional<UserRef> resolved = service.resolve(new UsernameKey("alice"));

        assertTrue(resolved.isPresent());
        assertEquals(new UserRef(8L, "alice", Set.of("USER")), resolved.get());
        assertNull(service.lastResolvedId);
        assertEquals("alice", service.lastResolvedUsername);
    }

    @Test
    void applicationPrincipalConvenienceMethodsHandleNulls() {
        ApplicationPrincipal principal = new TestPrincipal(null, "alice", null);

        assertTrue(principal.userId().isEmpty());
        assertEquals(Optional.of("alice"), principal.username());
        assertTrue(principal.roles().isEmpty());
        assertFalse(principal.hasRole("ADMIN"));
    }

    @Test
    void principalResolverCurrentDelegatesToCurrentOrNull() {
        PrincipalResolver resolver = () -> new TestPrincipal(1L, "alice", Set.of("ADMIN"));

        assertEquals("alice", resolver.current().getUsername());
    }

    @Test
    void principalResolverCurrentThrowsWhenPrincipalMissing() {
        PrincipalResolver resolver = () -> null;

        IllegalStateException exception = assertThrows(IllegalStateException.class, resolver::current);
        assertTrue(exception.getMessage().contains("No authentication principal available"));
    }

    private static final class RecordingIdentityService implements IdentityService {
        private Long lastResolvedId;
        private String lastResolvedUsername;

        @Override
        public Optional<UserRef> findById(Long userId) {
            lastResolvedId = userId;
            return Optional.of(new UserRef(userId, "id-user", Set.of("ADMIN")));
        }

        @Override
        public Optional<UserRef> findByUsername(String username) {
            lastResolvedUsername = username;
            return Optional.of(new UserRef(8L, username, Set.of("USER")));
        }
    }

    private record TestPrincipal(Long principalUserId, String principalUsername, Set<String> principalRoles)
            implements ApplicationPrincipal {
        @Override
        public Long getUserId() {
            return principalUserId;
        }

        @Override
        public String getUsername() {
            return principalUsername;
        }

        @Override
        public Set<String> getRoles() {
            return principalRoles;
        }
    }
}
