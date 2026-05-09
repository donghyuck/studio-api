/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file IpAddressLiterals.java
 *      @date 2026
 *
 */

package studio.one.base.security.audit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class IpAddressLiterals {

    private IpAddressLiterals() {
    }

    public static String normalizeOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        String ipv4 = normalizeIpv4(trimmed);
        if (ipv4 != null) {
            return ipv4;
        }
        return normalizeIpv6(trimmed);
    }

    public static boolean isValid(String value) {
        return normalizeOrNull(value) != null;
    }

    public static boolean isInAnyCidr(String value, Collection<String> cidrs) {
        if (cidrs == null || cidrs.isEmpty()) {
            return false;
        }
        byte[] address = addressBytes(value);
        if (address == null) {
            return false;
        }
        return cidrs.stream().anyMatch(cidr -> matchesCidr(address, cidr));
    }

    private static boolean matchesCidr(byte[] address, String cidr) {
        if (cidr == null || cidr.isBlank()) {
            return false;
        }
        String[] parts = cidr.trim().split("/", -1);
        byte[] network = addressBytes(parts[0]);
        if (network == null || network.length != address.length) {
            return false;
        }
        int prefixLength = network.length * 8;
        if (parts.length == 2) {
            try {
                prefixLength = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ex) {
                return false;
            }
        } else if (parts.length > 2) {
            return false;
        }
        if (prefixLength < 0 || prefixLength > network.length * 8) {
            return false;
        }
        return matchesPrefix(address, network, prefixLength);
    }

    private static byte[] addressBytes(String value) {
        String normalized = normalizeOrNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return InetAddress.getByName(normalized).getAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private static boolean matchesPrefix(byte[] address, byte[] network, int prefixLength) {
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (address[i] != network[i]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = (0xFF << (8 - remainingBits)) & 0xFF;
        return (address[fullBytes] & mask) == (network[fullBytes] & mask);
    }

    private static String normalizeIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] numbers = new int[4];
        for (int partIndex = 0; partIndex < parts.length; partIndex++) {
            String part = parts[partIndex];
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            int number = 0;
            for (int i = 0; i < part.length(); i++) {
                char ch = part.charAt(i);
                if (ch < '0' || ch > '9') {
                    return null;
                }
                number = number * 10 + (ch - '0');
            }
            if (number > 255) {
                return null;
            }
            numbers[partIndex] = number;
        }
        return IntStream.of(numbers)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining("."));
    }

    private static String normalizeIpv6(String value) {
        if (!value.contains(":") || value.contains("/") || value.contains("%")
                || value.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        try {
            String normalized = InetAddress.getByName(value).getHostAddress();
            return normalized.contains("%") ? null : normalized;
        } catch (UnknownHostException ex) {
            return null;
        }
    }
}
