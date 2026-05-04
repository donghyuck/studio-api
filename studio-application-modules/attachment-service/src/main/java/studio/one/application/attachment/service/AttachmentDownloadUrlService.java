package studio.one.application.attachment.service;

import studio.one.application.attachment.domain.model.Attachment;

public interface AttachmentDownloadUrlService {

    AttachmentDownloadUrl issueDownloadUrl(
            Attachment attachment,
            Long ttlSeconds,
            AttachmentDownloadUrlEndpointKind endpointKind,
            AttachmentDownloadUrlIssueActor actor,
            String clientIp,
            String userAgent);
}
