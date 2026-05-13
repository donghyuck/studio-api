package studio.one.application.attachment.web.dto.response;

import java.time.Instant;

public class AttachmentDownloadUrlDto {

    private final String url;
    private final Instant expiresAt;

    public AttachmentDownloadUrlDto(
            String url,
            Instant expiresAt) {
        this.url = url;
        this.expiresAt = expiresAt;
    }

    public String url() { return url; }

    public Instant expiresAt() { return expiresAt; }

    public String getUrl() { return url; }

    public Instant getExpiresAt() { return expiresAt; }

}
