package studio.one.application.attachment.thumbnail;

public class ThumbnailData {
    private final byte[] bytes;
    private final String contentType;

    public ThumbnailData(byte[] bytes, String contentType) {
        this.bytes = bytes;
        this.contentType = contentType;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String getContentType() {
        return contentType;
    }
}
