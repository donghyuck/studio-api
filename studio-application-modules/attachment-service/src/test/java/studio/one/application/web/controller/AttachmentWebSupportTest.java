package studio.one.application.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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
}
