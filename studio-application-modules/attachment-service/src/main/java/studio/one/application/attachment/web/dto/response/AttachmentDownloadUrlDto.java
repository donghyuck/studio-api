package studio.one.application.attachment.web.dto.response;

import java.time.Instant;

public record AttachmentDownloadUrlDto(String url, Instant expiresAt) {
}
