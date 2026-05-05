package studio.one.application.attachment.service;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.exception.AttachmentDownloadTokenInvalidException;

public interface AttachmentDownloadUrlService {

    AttachmentDownloadUrl issueDownloadUrl(
            Attachment attachment,
            Long ttlSeconds,
            AttachmentDownloadUrlEndpointKind endpointKind,
            AttachmentDownloadUrlIssueActor actor,
            String clientIp,
            String userAgent);

    default AttachmentDownloadTokenClaims verifyDownloadToken(String token) {
        throw new AttachmentDownloadTokenInvalidException();
    }

    default AttachmentDownloadTokenInspection inspectDownloadToken(String token) {
        String tokenHash = token == null || token.isBlank()
                ? null
                : AttachmentDownloadTokenCodec.sha256HexValue(token);
        try {
            return AttachmentDownloadTokenInspection.valid(verifyDownloadToken(token), tokenHash);
        } catch (AttachmentDownloadTokenInvalidException ex) {
            return AttachmentDownloadTokenInspection.invalid(tokenHash);
        }
    }
}
