package studio.one.platform.thumbnail;

import java.util.Arrays;

public record ThumbnailResult(byte[] bytes, String contentType, String format) {

    public ThumbnailResult {
        bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
        contentType = contentType == null ? ThumbnailFormats.contentType(format) : contentType;
        format = ThumbnailFormats.normalize(format);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
