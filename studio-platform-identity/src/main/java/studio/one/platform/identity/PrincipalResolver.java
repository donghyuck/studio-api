package studio.one.platform.identity;

/**
 * Resolves the current application principal from the active execution context.
 */
public interface PrincipalResolver {

    /**
     * Returns the current principal or throws when no authenticated principal is available.
     *
     * @throws IllegalStateException when no principal can be resolved
     */
    default ApplicationPrincipal current() {
        ApplicationPrincipal principal = currentOrNull();
        if (principal == null) {
            throw new IllegalStateException("No authentication principal available");
        }
        return principal;
    }

    /**
     * Returns the current principal when available.
     *
     * <p>Implementations should return {@code null} when no principal can be resolved.
     * They should not return an anonymous placeholder unless that placeholder is an
     * intentional application principal.</p>
     */
    ApplicationPrincipal currentOrNull();
}
