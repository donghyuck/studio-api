package studio.one.application.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AttachmentUrlIssueRequestDetailsResolverTest {

    @Test
    void ignoresForwardedForByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        request.addHeader("User-Agent", "JUnit");

        AttachmentUrlIssueRequestDetails details =
                new AttachmentUrlIssueRequestDetailsResolver(false).resolve(request);

        assertEquals("10.0.0.10", details.clientIp());
        assertEquals("JUnit", details.userAgent());
    }

    @Test
    void trustsForwardedForOnlyWhenEnabled() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

        AttachmentUrlIssueRequestDetails details =
                new AttachmentUrlIssueRequestDetailsResolver(true).resolve(request);

        assertEquals("203.0.113.10", details.clientIp());
    }
}
