package studio.one.application.attachment.application.command;

import studio.one.application.attachment.application.result.*;

import java.time.Instant;

public class AttachmentDownloadUrlIssueAuditLogQuery {

    private final Long attachmentId;
    private final Integer objectType;
    private final Long objectId;
    private final AttachmentDownloadUrlEndpointKind endpointKind;
    private final String issuedByPrincipalName;
    private final Instant from;
    private final Instant to;

    public AttachmentDownloadUrlIssueAuditLogQuery(
            Long attachmentId,
            Integer objectType,
            Long objectId,
            AttachmentDownloadUrlEndpointKind endpointKind,
            String issuedByPrincipalName,
            Instant from,
            Instant to) {
        this.attachmentId = attachmentId;
        this.objectType = objectType;
        this.objectId = objectId;
        this.endpointKind = endpointKind;
        this.issuedByPrincipalName = issuedByPrincipalName;
        this.from = from;
        this.to = to;
    }

    public Long attachmentId() { return attachmentId; }

    public Integer objectType() { return objectType; }

    public Long objectId() { return objectId; }

    public AttachmentDownloadUrlEndpointKind endpointKind() { return endpointKind; }

    public String issuedByPrincipalName() { return issuedByPrincipalName; }

    public Instant from() { return from; }

    public Instant to() { return to; }

}
