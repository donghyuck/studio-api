package studio.one.application.webknowledge.infrastructure.web;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.hc.client5.http.DnsResolver;

/**
 * Resolves and validates the exact addresses used by the HTTP connection.
 */
final class PublicOnlyDnsResolver implements DnsResolver {

    private final AddressLookup addressLookup;

    PublicOnlyDnsResolver() {
        this(InetAddress::getAllByName);
    }

    PublicOnlyDnsResolver(AddressLookup addressLookup) {
        this.addressLookup = addressLookup;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        try {
            return WebUrlPolicy.requirePublicAddresses(addressLookup.resolve(host));
        } catch (WebPageFetchException ex) {
            UnknownHostException failure = new UnknownHostException(ex.errorCode());
            failure.initCause(ex);
            throw failure;
        }
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        if (host == null || host.isBlank()) {
            throw new UnknownHostException("HOST_RESOLUTION_FAILED");
        }
        resolve(host);
        return host;
    }

    @FunctionalInterface
    interface AddressLookup {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
