package studio.one.application.web.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import studio.one.platform.identity.ApplicationPrincipal;

class AttachmentAccessSupportTest {

    @Test
    void isAdminAcceptsLegacyAndSpringSecurityRoleNames() {
        assertTrue(AttachmentAccessSupport.isAdmin(principal("ADMIN")));
        assertTrue(AttachmentAccessSupport.isAdmin(principal("ROLE_ADMIN")));
    }

    private ApplicationPrincipal principal(String role) {
        return new ApplicationPrincipal() {
            @Override
            public Long getUserId() {
                return 1L;
            }

            @Override
            public String getUsername() {
                return "admin";
            }

            @Override
            public Set<String> getRoles() {
                return Set.of(role);
            }
        };
    }
}
