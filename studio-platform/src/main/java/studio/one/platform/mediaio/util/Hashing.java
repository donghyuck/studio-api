package studio.one.platform.mediaio.util;

import java.security.MessageDigest;

public final class Hashing {
    private Hashing() {}
    public static String sha256Hex(byte[] bytes) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return toHex(md.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            hex[i * 2] = digits[value >>> 4];
            hex[i * 2 + 1] = digits[value & 0x0f];
        }
        return new String(hex);
    }

}
