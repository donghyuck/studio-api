package studio.one.application.webknowledge.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.InetAddress;

import org.junit.jupiter.api.Test;

class WebUrlPolicyTest {

    @Test
    void normalizesHttpsUrlAndRemovesFragment() {
        URI value = WebUrlPolicy.normalize("https://Example.ORG:443/path?q=1#fragment");

        assertEquals("https://example.org/path?q=1", value.toString());
    }

    @Test
    void rejectsHttpUserInfoAndLocalhost() {
        assertThrows(WebPageFetchException.class, () -> WebUrlPolicy.normalize("http://example.org"));
        assertThrows(WebPageFetchException.class, () -> WebUrlPolicy.normalize("https://user@example.org"));
        assertThrows(WebPageFetchException.class,
                () -> WebUrlPolicy.assertPublicHost(URI.create("https://127.0.0.1/")));
        assertThrows(WebPageFetchException.class,
                () -> WebUrlPolicy.assertPublicHost(URI.create("https://[::1]/")));
        assertThrows(WebPageFetchException.class,
                () -> WebUrlPolicy.normalize("https://example.org/page?access_token=secret"));
    }

    @Test
    void rejectsReservedDocumentationAndBenchmarkNetworks() throws Exception {
        assertThrows(WebPageFetchException.class,
                () -> WebUrlPolicy.requirePublicAddresses(
                        new InetAddress[] { InetAddress.getByName("192.0.2.1") }));
        assertThrows(WebPageFetchException.class,
                () -> WebUrlPolicy.requirePublicAddresses(
                        new InetAddress[] { InetAddress.getByName("198.18.0.1") }));
        assertThrows(WebPageFetchException.class,
                () -> WebUrlPolicy.requirePublicAddresses(
                        new InetAddress[] { InetAddress.getByName("2001:db8::1") }));
    }

    @Test
    void acceptsOnlyWhenEveryResolvedAddressIsPublic() throws Exception {
        InetAddress publicAddress = InetAddress.getByName("93.184.216.34");
        InetAddress privateAddress = InetAddress.getByName("10.0.0.1");

        assertEquals(
                publicAddress,
                WebUrlPolicy.requirePublicAddresses(new InetAddress[] { publicAddress })[0]);
        assertThrows(
                WebPageFetchException.class,
                () -> WebUrlPolicy.requirePublicAddresses(
                        new InetAddress[] { publicAddress, privateAddress }));
    }

    @Test
    void normalizedUrlHashIsStable() {
        assertEquals(
                WebUrlPolicy.sha256("https://example.org/"),
                WebUrlPolicy.sha256(WebUrlPolicy.normalize("https://EXAMPLE.org").toString()));
    }
}
