package studio.one.application.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import studio.one.platform.identity.ApplicationPrincipal;

class AttachmentWebSupportTest {

    @Test
    void sanitizeFilenameRemovesPathSegments() {
        assertEquals("contract.pdf", AttachmentWebSupport.sanitizeFilename("dir/subdir/contract.pdf"));
        assertEquals("contract.pdf", AttachmentWebSupport.sanitizeFilename("dir\\subdir\\contract.pdf"));
        assertNull(AttachmentWebSupport.sanitizeFilename(" "));
    }

    @Test
    void resolveMediaTypeFallsBackToOctetStream() {
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, AttachmentWebSupport.resolveMediaType(null));
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, AttachmentWebSupport.resolveMediaType("not a type"));
    }

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
