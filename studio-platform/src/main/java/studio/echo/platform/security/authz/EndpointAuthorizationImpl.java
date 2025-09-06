package studio.echo.platform.security.authz;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides the core logic for endpoint authorization using Spring Expression
 * Language (SpEL). This class checks if the current user has the required
 * roles to perform an action on a resource.
 *
 * @author donghyuck, son
 * @since 2025-09-01
 * @version 1.0
 */
@RequiredArgsConstructor
@Slf4j
public class EndpointAuthorizationImpl {
    private static final String ADMIN = "ADMIN";
    private static final String ROLE_PREFIX = "ROLE_";

    private final DomainPolicyRegistry registry;

    private final EndpointModeGuard modeGuard;

    // ===== SpEL API =====

    /**
     * Checks if the current user can perform an action on a resource.
     *
     * @param resource the resource to check
     * @param action   the action to perform
     * @return {@code true} if the user is authorized, {@code false} otherwise
     */
    public boolean can(String resource, String action) {
        return check(resourceKey(resource), normalizeAction(action));
    }

    /**
     * Checks if the current user can perform an action on a resource.
     *
     * @param domain    the domain of the resource
     * @param component the component of the resource
     * @param action    the action to perform
     * @return {@code true} if the user is authorized, {@code false} otherwise
     */
    public boolean can(String domain, String component, String action) {
        return check(resourceKey(domain, component), normalizeAction(action));
    }

    /**
     * Checks if the current user can perform any of the specified actions on a
     * resource.
     *
     * @param resource the resource to check
     * @param actions  the actions to check
     * @return {@code true} if the user is authorized for at least one action,
     *         {@code false} otherwise
     */
    public boolean any(String resource, String... actions) {
        if (actions == null || actions.length == 0)
            return false;
        String key = resourceKey(resource);
        for (String a : actions)
            if (check(key, normalizeAction(a)))
                return true;
        return false;
    }

    /**
     * Checks if the current user can perform any of the specified actions on a
     * resource.
     *
     * @param domain    the domain of the resource
     * @param component the component of the resource
     * @param actions   the actions to check
     * @return {@code true} if the user is authorized for at least one action,
     *         {@code false} otherwise
     */
    public boolean any(String domain, String component, String... actions) {
        if (actions == null || actions.length == 0)
            return false;
        String key = resourceKey(domain, component);
        for (String a : actions)
            if (check(key, normalizeAction(a)))
                return true;
        return false;
    }

    // ===== Core =====

    private boolean check(String resourceKey, String action) {
        Authentication auth = currentAuth();
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities() == null) {
            log.trace("AuthZ DENY (unauthenticated): resource={}, action={}", resourceKey, action);
            return false;
        }

        // 1) 모드 게이트 (도메인 기준)
        Parsed parsed = parseResource(resourceKey);
        if (modeGuard != null && parsed.domain != null && !modeGuard.allows(parsed.domain, action)) {
            log.trace("AuthZ DENY by MODE: domain={}, action={}, resource={}", parsed.domain, action, resourceKey);
            return false;
        }

        // 2) ADMIN 슈퍼 롤
        Set<String> granted = toRoleNames(auth.getAuthorities());
        if (granted.contains(ADMIN)) {
            log.trace("AuthZ ALLOW by ADMIN: resource={}, action={}", resourceKey, action);
            return true;
        }

        // 3) 정책 조회 + write→read 폴백
        List<String> required = safe(registry.requiredRoles(resourceKey, action));
        if (required.isEmpty() && "write".equals(action)) {
            required = safe(registry.requiredRoles(resourceKey, "read"));
        }
        if (required.isEmpty()) {
            log.trace("AuthZ DENY (no policy): resource={}, action={}", resourceKey, action);
            return false;
        }

        boolean allowed = required.stream().anyMatch(granted::contains);
        if (log.isTraceEnabled()) {
            log.trace("AuthZ {} resource={}, action={}, required={}, granted={}",
                    allowed ? "ALLOW" : "DENY", resourceKey, action, required, granted);
        }
        return allowed;
    }

    private Authentication currentAuth() {
        var ctx = SecurityContextHolder.getContext();
        return (ctx != null) ? ctx.getAuthentication() : null;
    }

    private String resourceKey(String resource) {
        return resource == null ? "" : resource.toLowerCase(Locale.ROOT).trim();
    }

    private String resourceKey(String domain, String component) {
        String d = domain == null ? "" : domain.toLowerCase(Locale.ROOT).trim();
        String c = component == null ? "" : component.toLowerCase(Locale.ROOT).trim();
        return d.isEmpty() ? c : (c.isEmpty() ? d : d + ":" + c);
    }

    private String normalizeAction(String action) {
        if (action == null)
            return "read";
        switch (action.toLowerCase(Locale.ROOT)) {
            case "access":
            case "view":
            case "read":
                return "read";
            case "manage":
            case "write":
                return "write";
            case "admin":
                return "admin";
            default:
                return action.toLowerCase(Locale.ROOT);
        }
    }

    private Set<String> toRoleNames(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null)
            return Collections.emptySet();
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.startsWith(ROLE_PREFIX) ? s.substring(5) : s)
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static <T> List<T> safe(List<T> v) {
        return v == null ? java.util.Collections.emptyList() : v;
    }

    private static class Parsed {
        
        final String domain;
        final String component;

        Parsed(String d, String c) {
            this.domain = d;
            this.component = c;
        }
    }

    private static Parsed parseResource(String resource) {
        if (resource == null)
            return new Parsed(null, null);
        String s = resource.trim();
        int i = s.indexOf(':');
        if (i < 0)
            return new Parsed(s, null);
        return new Parsed(s.substring(0, i), s.substring(i + 1));
    }
}