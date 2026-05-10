package studio.one.application.attachment.application.result;

public record AttachmentDownloadTokenInspection(
        AttachmentDownloadTokenInspectionStatus status,
        AttachmentDownloadTokenClaims claims,
        String tokenHash) {

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
