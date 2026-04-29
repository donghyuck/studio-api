package studio.one.platform.thumbnail;

import java.util.Arrays;

public record ThumbnailSource(String contentType, String filename, byte[] bytes) {

    public ThumbnailSource {
        contentType = contentType == null ? "" : contentType;
        filename = filename == null ? "" : filename;
        bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    public int size() {
        return bytes.length;
    }
}
