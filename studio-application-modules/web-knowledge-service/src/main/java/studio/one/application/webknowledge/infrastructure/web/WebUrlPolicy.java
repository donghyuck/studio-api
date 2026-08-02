package studio.one.application.webknowledge.infrastructure.web;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

public final class WebUrlPolicy {

    public static final int MAX_URL_LENGTH = 2_048;
    private static final Set<String> CREDENTIAL_QUERY_KEYS = Set.of(
            "access_token", "api_key", "apikey", "auth", "key", "password",
            "sig", "signature", "token");

    private WebUrlPolicy() {
    }

    public static URI normalize(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_URL_LENGTH) {
            throw new WebPageFetchException("INVALID_URL");
        }
        try {
            URI uri = URI.create(value.trim()).normalize();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new WebPageFetchException("URL_NOT_ALLOWED");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (containsCredentialQuery(uri.getRawQuery())) {
                throw new WebPageFetchException("URL_CREDENTIALS_NOT_ALLOWED");
            }
            return new URI(
                    "https",
                    null,
                    host,
                    uri.getPort() == 443 ? -1 : uri.getPort(),
                    uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath(),
                    uri.getRawQuery(),
                    null);
        } catch (WebPageFetchException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new WebPageFetchException("INVALID_URL", ex);
        }
    }

    private static boolean containsCredentialQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        for (String part : query.split("&")) {
            String key;
            try {
                key = URLDecoder.decode(part.split("=", 2)[0], StandardCharsets.UTF_8)
                        .toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException ex) {
                return true;
            }
            if (CREDENTIAL_QUERY_KEYS.contains(key)) {
                return true;
            }
        }
        return false;
    }

    public static InetAddress[] resolvePublicAddresses(String host) {
        if (host == null || host.isBlank()) {
            throw new WebPageFetchException("HOST_RESOLUTION_FAILED");
        }
        try {
            return requirePublicAddresses(InetAddress.getAllByName(host));
        } catch (UnknownHostException ex) {
            throw new WebPageFetchException("HOST_RESOLUTION_FAILED", ex);
        }
    }

    static InetAddress[] requirePublicAddresses(InetAddress[] addresses) {
        if (addresses == null
                || addresses.length == 0
                || Arrays.stream(addresses).anyMatch(WebUrlPolicy::blocked)) {
            throw new WebPageFetchException("HOST_NOT_PUBLIC");
        }
        return addresses.clone();
    }

    public static void assertPublicHost(URI uri) {
        resolvePublicAddresses(uri == null ? null : uri.getHost());
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    static boolean blocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first == 0
                    || first == 10
                    || first == 127
                    || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 0 && third == 0)
                    || (first == 192 && second == 0 && third == 2)
                    || (first == 192 && second == 88 && third == 99)
                    || (first == 192 && second == 168)
                    || (first == 198 && (second == 18 || second == 19))
                    || (first == 198 && second == 51 && third == 100)
                    || (first == 203 && second == 0 && third == 113);
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return (first & 0xfe) == 0xfc
                    || (first == 0xfe && (second & 0xc0) == 0x80)
                    || first == 0xff
                    || documentationIpv6(bytes)
                    || transitionIpv6(bytes);
        }
        return true;
    }

    private static boolean documentationIpv6(byte[] bytes) {
        return Byte.toUnsignedInt(bytes[0]) == 0x20
                && Byte.toUnsignedInt(bytes[1]) == 0x01
                && Byte.toUnsignedInt(bytes[2]) == 0x0d
                && Byte.toUnsignedInt(bytes[3]) == 0xb8;
    }

    private static boolean transitionIpv6(byte[] bytes) {
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return (first == 0x20 && second == 0x02)
                || (first == 0x00 && second == 0x64
                        && Byte.toUnsignedInt(bytes[2]) == 0xff
                        && Byte.toUnsignedInt(bytes[3]) == 0x9b);
    }
}
