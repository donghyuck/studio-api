package studio.one.platform.identity;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Application-level authenticated principal contract.
 *
 * <p>This contract intentionally avoids direct dependency on Spring Security or a
 * specific user domain model. Implementations may represent a fully identified
 * application user, a service account, or an adapter-backed principal.</p>
 */
public interface ApplicationPrincipal {

    /**
     * Returns the application user id when available.
     *
     * <p>Implementations may return {@code null} for anonymous, external, or
     * service-account principals that do not map to a local user id.</p>
     */
    Long getUserId();

    /**
     * Returns the username when available.
     *
     * <p>Implementations may return {@code null} when the principal has no stable
     * username representation.</p>
     */
    String getUsername();

    /**
     * Returns the principal's roles.
     *
     * <p>Implementations should return an empty set when no roles are available and
     * should not return {@code null}. {@link #roles()} keeps backward compatibility
     * for legacy implementations that still do.</p>
     */
    Set<String> getRoles();

    default Optional<Long> userId() {
        return Optional.ofNullable(getUserId());
    }

    default Optional<String> username() {
        return Optional.ofNullable(getUsername());
    }

    default Set<String> roles() {
        Set<String> roles = getRoles();
        return roles == null ? Collections.emptySet() : roles;
    }

    default boolean hasRole(String role) {
        return roles().contains(role);
    }
}
