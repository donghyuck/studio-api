package studio.one.application.attachment.application.result;

public class AttachmentDownloadTokenInspection {

    private final AttachmentDownloadTokenInspectionStatus status;
    private final AttachmentDownloadTokenClaims claims;
    private final String tokenHash;

    public AttachmentDownloadTokenInspection(
            AttachmentDownloadTokenInspectionStatus status,
            AttachmentDownloadTokenClaims claims,
            String tokenHash) {
        this.status = status;
        this.claims = claims;
        this.tokenHash = tokenHash;
    }

    public AttachmentDownloadTokenInspectionStatus status() { return status; }

    public AttachmentDownloadTokenClaims claims() { return claims; }

    public String tokenHash() { return tokenHash; }

public static AttachmentDownloadTokenInspection valid(
            AttachmentDownloadTokenClaims claims,
            String tokenHash) {
        return new AttachmentDownloadTokenInspection(
                AttachmentDownloadTokenInspectionStatus.VALID,
                claims,
                tokenHash);
    }

    public static AttachmentDownloadTokenInspection expired(
            AttachmentDownloadTokenClaims claims,
            String tokenHash) {
        return new AttachmentDownloadTokenInspection(
                AttachmentDownloadTokenInspectionStatus.EXPIRED,
                claims,
                tokenHash);
    }

    public static AttachmentDownloadTokenInspection invalid(String tokenHash) {
        return new AttachmentDownloadTokenInspection(
                AttachmentDownloadTokenInspectionStatus.INVALID_TOKEN,
                null,
                tokenHash);
    }
}
