package studio.one.platform.mediaio.util;

import java.io.*;

public final class BytesUtil {
    
    private BytesUtil() {
    }

    public static byte[] readAll(InputStream in) throws IOException {
        try (in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1)
                bos.write(buf, 0, r);
            return bos.toByteArray();
        }
    }

    public static long count(InputStream in) throws IOException {
        try (in) {
            byte[] buf = new byte[8192];
            long n = 0;
            int r;
            while ((r = in.read(buf)) != -1)
                n += r;
            return n;
        }
    }
}
