package studio.one.application.attachment.thumbnail;

public class ThumbnailData {
    private final byte[] bytes;
    private final String contentType;
    private final String status;

    public ThumbnailData(byte[] bytes, String contentType) {
        this(bytes, contentType, "ready");
    }

    public ThumbnailData(byte[] bytes, String contentType, String status) {
        this.bytes = bytes;
        this.contentType = contentType;
        this.status = status;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String getContentType() {
        return contentType;
    }

    public String getStatus() {
        return status;
    }

    public boolean isPending() {
        return "pending".equalsIgnoreCase(status);
    }
}
