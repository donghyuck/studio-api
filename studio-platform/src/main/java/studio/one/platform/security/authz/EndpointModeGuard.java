package studio.one.platform.security.authz;

/**
 * A guard that can block access to endpoints based on the domain and action.
 *
 * @author donghyuck, son
 * @since 2025-09-01
 * @version 1.0
 */
public interface EndpointModeGuard {

    /**
     * Checks if access is allowed for the specified domain and action.
     *
     * @param domain the domain of the resource
     * @param action the action to perform
     * @return {@code true} if access is allowed, {@code false} otherwise
     */
    boolean allows(String domain, String action);

}
