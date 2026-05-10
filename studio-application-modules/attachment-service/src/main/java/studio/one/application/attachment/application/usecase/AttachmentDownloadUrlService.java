package studio.one.application.attachment.application.usecase;

import studio.one.application.attachment.application.command.*;
import studio.one.application.attachment.application.result.*;
import studio.one.application.attachment.application.service.AttachmentDownloadTokenCodec;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.application.error.AttachmentDownloadTokenInvalidException;

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
        String tokenHash = AttachmentDownloadTokenCodec.tokenHashForAudit(token);
        try {
            return AttachmentDownloadTokenInspection.valid(verifyDownloadToken(token), tokenHash);
        } catch (AttachmentDownloadTokenInvalidException ex) {
            return AttachmentDownloadTokenInspection.invalid(tokenHash);
        }
    }
}
