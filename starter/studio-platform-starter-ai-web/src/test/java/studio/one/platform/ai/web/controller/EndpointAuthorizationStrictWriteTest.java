package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import studio.one.platform.security.authz.DomainPolicyRegistry;
import studio.one.platform.security.authz.EndpointAuthorizationImpl;

class EndpointAuthorizationStrictWriteTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void strictWriteDoesNotUseExistingWriteToReadFallback() {
        DomainPolicyRegistry registry = (resource, action) -> {
            if ("services:ai_rag".equals(resource) && "read".equals(action)) {
                return List.of("RAG_READER");
            }
            return List.of();
        };
        EndpointAuthorizationImpl authorization = new EndpointAuthorizationImpl(registry, (domain, action) -> true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "reader",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_RAG_READER"))));

        assertThat(authorization.can("services:ai_rag", "write")).isTrue();
        assertThat(authorization.canStrict("services:ai_rag", "write")).isFalse();
    }

    @Test
    void strictWriteStillHonorsAdminAndModeGuard() {
        DomainPolicyRegistry registry = (resource, action) -> List.of();
        EndpointAuthorizationImpl modeDenied = new EndpointAuthorizationImpl(registry, (domain, action) -> false);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThat(modeDenied.canStrict("services:ai_rag", "write")).isFalse();

        EndpointAuthorizationImpl modeAllowed = new EndpointAuthorizationImpl(registry, (domain, action) -> true);
        assertThat(modeAllowed.canStrict("services:ai_rag", "write")).isTrue();
    }
}
