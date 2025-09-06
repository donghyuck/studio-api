package studio.echo.platform.security.authz;

import java.util.List;

/**
 * A registry for domain policies that provides access to the roles required
 * for a given resource and action.
 *
 * @author donghyuck, son
 * @since 2025-09-01
 * @version 1.0
 */
public interface DomainPolicyRegistry {

    /**
     * Returns the list of roles required to perform an action on a resource.
     *
     * @param resource the resource key, in the format "group" or "user:profile"
     * @param action   the action to perform (e.g., "read", "write", "admin")
     * @return a list of required roles (e.g., ["ADMIN", "MANAGER"]), or an empty
     *         list if no roles are required
     */
    List<String> requiredRoles(String resource, String action);
    
}