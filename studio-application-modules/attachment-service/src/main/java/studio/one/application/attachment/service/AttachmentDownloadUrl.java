package studio.one.application.attachment.service;

import java.time.Instant;

public record AttachmentDownloadUrl(String url, Instant expiresAt) {
}
