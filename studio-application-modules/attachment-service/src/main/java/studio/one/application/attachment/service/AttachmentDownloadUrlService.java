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
}
