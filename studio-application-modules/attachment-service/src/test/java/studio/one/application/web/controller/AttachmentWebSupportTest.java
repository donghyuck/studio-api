package studio.one.application.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import studio.one.platform.identity.ApplicationPrincipal;

class AttachmentWebSupportTest {

    @Test
    void sanitizeFilenameKeepsLeafNameOnly() {
        assertEquals("report.pdf", AttachmentWebSupport.sanitizeFilename("folder/subfolder/report.pdf"));
    }

    @Test
    void resolveMediaTypeFallsBackToOctetStream() {
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, AttachmentWebSupport.resolveMediaType("not-a-type"));
    }

    @Test
    void downloadHeadersIncludeDispositionAndContentLength() {
        HttpHeaders headers = AttachmentWebSupport.downloadHeaders("text/plain", 12L, "note.txt");

        assertEquals(MediaType.TEXT_PLAIN, headers.getContentType());
        assertEquals(12L, headers.getContentLength());
        assertTrue(headers.getContentDisposition().isAttachment());
    }

    @Test
    void isAdminAcceptsAdminAndRoleAdmin() {
        assertTrue(AttachmentWebSupport.isAdmin(principal("ADMIN")));
        assertTrue(AttachmentWebSupport.isAdmin(principal("ROLE_ADMIN")));
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
            public java.util.Set<String> getRoles() {
                return java.util.Set.of(role);
            }
        };
    }
}
