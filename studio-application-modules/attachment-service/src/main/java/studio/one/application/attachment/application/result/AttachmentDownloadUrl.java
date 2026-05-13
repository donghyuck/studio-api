package studio.one.application.attachment.application.result;

import java.time.Instant;

public class AttachmentDownloadUrl {

    private final String url;
    private final Instant expiresAt;

    public AttachmentDownloadUrl(
            String url,
            Instant expiresAt) {
        this.url = url;
        this.expiresAt = expiresAt;
    }

    public String url() { return url; }

    public Instant expiresAt() { return expiresAt; }

}
