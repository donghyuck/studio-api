package studio.one.application.attachment.application.command;

import studio.one.application.attachment.application.result.*;

import java.time.Instant;

public class AttachmentDownloadAuditLogQuery {

    private final Long attachmentId;
    private final Integer objectType;
    private final Long objectId;
    private final String tokenHash;
    private final AttachmentDownloadAuditResult result;
    private final Instant from;
    private final Instant to;
    private final String clientIp;

    public AttachmentDownloadAuditLogQuery(
            Long attachmentId,
            Integer objectType,
            Long objectId,
            String tokenHash,
            AttachmentDownloadAuditResult result,
            Instant from,
            Instant to,
            String clientIp) {
        this.attachmentId = attachmentId;
        this.objectType = objectType;
        this.objectId = objectId;
        this.tokenHash = tokenHash;
        this.result = result;
        this.from = from;
        this.to = to;
        this.clientIp = clientIp;
    }

    public Long attachmentId() { return attachmentId; }

    public Integer objectType() { return objectType; }

    public Long objectId() { return objectId; }

    public String tokenHash() { return tokenHash; }

    public AttachmentDownloadAuditResult result() { return result; }

    public Instant from() { return from; }

    public Instant to() { return to; }

    public String clientIp() { return clientIp; }

}
