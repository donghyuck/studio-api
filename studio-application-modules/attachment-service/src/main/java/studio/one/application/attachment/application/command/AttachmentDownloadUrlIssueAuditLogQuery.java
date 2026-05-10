package studio.one.application.attachment.application.command;

import studio.one.application.attachment.application.result.*;

import java.time.Instant;

public record AttachmentDownloadUrlIssueAuditLogQuery(
        Long attachmentId,
        Integer objectType,
        Long objectId,
        AttachmentDownloadUrlEndpointKind endpointKind,
        String issuedByPrincipalName,
        Instant from,
        Instant to) {
}
