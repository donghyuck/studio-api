package studio.one.base.security.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

class ClientRequestDetailsTest {

    @Test
    void invalidForwardedIpFallsBackToRemoteAddress() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("not-an-ip");
        when(request.getHeader("User-Agent")).thenReturn("agent");
        when(request.getRemoteAddr()).thenReturn("203.0.113.1");

        ClientRequestDetails details = ClientRequestDetails.from(request, "X-Forwarded-For");

        assertEquals("203.0.113.1", details.getRemoteIp());
        assertEquals("not-an-ip", details.getForwardedFor());
    }

    @Test
    void ignoresValidForwardedIpAndUsesRemoteAddress() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("::ffff:192.0.2.128, 10.0.0.1");
        when(request.getRemoteAddr()).thenReturn("203.0.113.1");

        ClientRequestDetails details = ClientRequestDetails.from(request);

        assertEquals("203.0.113.1", details.getRemoteIp());
    }

    @Test
    void configuredForwardedHeaderIsNormalizedWhenValid() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("::ffff:192.0.2.128, 10.0.0.1");
        when(request.getRemoteAddr()).thenReturn("203.0.113.1");

        ClientRequestDetails details = ClientRequestDetails.from(request, "X-Forwarded-For",
                List.of("203.0.113.0/24", "10.0.0.0/8"));

        assertEquals("192.0.2.128", details.getRemoteIp());
        assertEquals("::ffff:192.0.2.128, 10.0.0.1", details.getForwardedFor());
    }

    @Test
    void forwardedChainUsesFirstUntrustedHopFromRight() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.77, 192.0.2.40");
        when(request.getRemoteAddr()).thenReturn("203.0.113.1");

        ClientRequestDetails details = ClientRequestDetails.from(request, "X-Forwarded-For",
                List.of("203.0.113.0/24"));

        assertEquals("192.0.2.40", details.getRemoteIp());
    }

    @Test
    void configuredForwardedHeaderIsIgnoredWhenRemoteAddressIsNotTrusted() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.99");
        when(request.getRemoteAddr()).thenReturn("203.0.113.1");

        ClientRequestDetails details = ClientRequestDetails.from(request, "X-Forwarded-For",
                List.of("10.0.0.0/8"));

        assertEquals("203.0.113.1", details.getRemoteIp());
        assertEquals("198.51.100.99", details.getForwardedFor());
    }

    @Test
    void stripsIpv4RemoteAddressPortBeforeValidation() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1:54321");

        ClientRequestDetails details = ClientRequestDetails.from(request);

        assertEquals("127.0.0.1", details.getRemoteIp());
    }

    @Test
    void normalizesIpv4MappedIpv6RemoteAddress() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("::ffff:192.0.2.128");

        ClientRequestDetails details = ClientRequestDetails.from(request);

        assertEquals("192.0.2.128", details.getRemoteIp());
    }

    @Test
    void nullRemoteAddressStaysNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        ClientRequestDetails details = ClientRequestDetails.from(request);

        assertNull(details.getRemoteIp());
    }
}
