package studio.one.application.attachment.application.result;

import java.time.Instant;

public record AttachmentDownloadUrl(String url, Instant expiresAt) {
}
