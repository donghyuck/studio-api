package studio.one.application.attachment.service;

import java.time.Instant;

public record AttachmentDownloadTokenClaims(
        long attachmentId,
        Instant issuedAt,
        Instant expiresAt,
        String nonce) {
}
