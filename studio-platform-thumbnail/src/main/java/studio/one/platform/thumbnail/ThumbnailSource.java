package studio.one.platform.thumbnail;

import java.util.Arrays;

public class ThumbnailSource {

    private final String contentType;
    private final String filename;
    private final byte[] bytes;

    public ThumbnailSource(String contentType, String filename, byte[] bytes) {
        this.contentType = contentType == null ? "" : contentType;
        this.filename = filename == null ? "" : filename;
        this.bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
    }

    public String contentType() { return contentType; }

    public String filename() { return filename; }

    public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }

    public int size() { return bytes.length; }
}
