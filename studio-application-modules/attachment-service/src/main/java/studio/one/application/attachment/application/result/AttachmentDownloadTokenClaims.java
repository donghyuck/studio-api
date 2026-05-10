package studio.one.application.attachment.application.result;

import java.time.Instant;

public record AttachmentDownloadTokenClaims(
        long attachmentId,
        Instant issuedAt,
        Instant expiresAt,
        String nonce) {
}
