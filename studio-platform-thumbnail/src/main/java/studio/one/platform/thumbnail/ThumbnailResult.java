package studio.one.platform.thumbnail;

import java.util.Arrays;

public class ThumbnailResult {

    private final byte[] bytes;
    private final String contentType;
    private final String format;

    public ThumbnailResult(byte[] bytes, String contentType, String format) {
        byte[] normalizedBytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
        String normalizedFormat = ThumbnailFormats.normalize(format);
        this.bytes = normalizedBytes;
        this.contentType = contentType == null ? ThumbnailFormats.contentType(normalizedFormat) : contentType;
        this.format = normalizedFormat;
    }

    public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }

    public String contentType() { return contentType; }

    public String format() { return format; }
}
