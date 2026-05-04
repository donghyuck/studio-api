package studio.one.application.web.dto;

import java.time.Instant;

public record AttachmentDownloadUrlDto(String url, Instant expiresAt) {
}
