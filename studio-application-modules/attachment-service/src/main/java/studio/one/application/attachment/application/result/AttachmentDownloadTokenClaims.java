package studio.one.application.attachment.application.result;

import java.time.Instant;

public class AttachmentDownloadTokenClaims {

    private final long attachmentId;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final String nonce;

    public AttachmentDownloadTokenClaims(
            long attachmentId,
            Instant issuedAt,
            Instant expiresAt,
            String nonce) {
        this.attachmentId = attachmentId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.nonce = nonce;
    }

    public long attachmentId() { return attachmentId; }

    public Instant issuedAt() { return issuedAt; }

    public Instant expiresAt() { return expiresAt; }

    public String nonce() { return nonce; }

}
