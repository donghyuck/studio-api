package studio.one.base.security.identity;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;

public class SecurityPrincipalResolver implements PrincipalResolver {

    @Override
    public ApplicationPrincipal current() {
        ApplicationPrincipal principal = currentOrNull();
        if (principal == null) {
            throw new IllegalStateException("No authentication principal available");
        }
        return principal;
    }

    @Override
    public ApplicationPrincipal currentOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof ApplicationPrincipal ap) {
            return ap;
        }
        if (principal instanceof UserDetails ud) {
            return new SimplePrincipal(null, ud.getUsername(), toRoles(ud.getAuthorities()));
        }
        if (principal instanceof String s && !s.isBlank()) {
            return new SimplePrincipal(null, s, toRoles(auth.getAuthorities()));
        }
        return null;
    }

    private Set<String> toRoles(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return Collections.emptySet();
        }
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }

    private record SimplePrincipal(Long id, String name, Set<String> roles) implements ApplicationPrincipal {
        @Override
        public Long getUserId() {
            return id;
        }

        @Override
        public String getUsername() {
            return name;
        }

        @Override
        public Set<String> getRoles() {
            return roles;
        }
    }
}
