package studio.one.application.webknowledge.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;

class PublicOnlyDnsResolverTest {

    @Test
    void returnsTheSameValidatedAddressUsedByTheConnection() throws Exception {
        InetAddress publicAddress = InetAddress.getByName("93.184.216.34");
        PublicOnlyDnsResolver resolver = new PublicOnlyDnsResolver(
                ignored -> new InetAddress[] { publicAddress });

        assertEquals(publicAddress, resolver.resolve("example.org")[0]);
    }

    @Test
    void rejectsPrivateAddressAtConnectionResolutionTime() throws Exception {
        PublicOnlyDnsResolver resolver = new PublicOnlyDnsResolver(
                ignored -> new InetAddress[] { InetAddress.getByName("169.254.169.254") });

        UnknownHostException error = assertThrows(
                UnknownHostException.class,
                () -> resolver.resolve("metadata.example"));

        assertEquals("HOST_NOT_PUBLIC", error.getMessage());
    }
}
