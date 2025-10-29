package studio.one.platform.mediaio.util;

import java.security.MessageDigest;
import java.util.HexFormat;

public final class Hashing {
    private Hashing() {}
    public static String sha256Hex(byte[] bytes) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
