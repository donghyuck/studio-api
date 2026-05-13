package studio.one.application.attachment.application.command;

import studio.one.application.attachment.application.result.*;

import java.time.Instant;

public class AttachmentDownloadAuditLogCommand {

    private final String tokenHash;
    private final Long attachmentId;
    private final Integer objectType;
    private final Long objectId;
    private final String linkType;
    private final Instant requestedAt;
    private final AttachmentDownloadAuditResult result;
    private final int httpStatus;
    private final Long downloadedBytes;
    private final String clientIp;
    private final String userAgent;
    private final String errorCode;

    public AttachmentDownloadAuditLogCommand(
            String tokenHash,
            Long attachmentId,
            Integer objectType,
            Long objectId,
            String linkType,
            Instant requestedAt,
            AttachmentDownloadAuditResult result,
            int httpStatus,
            Long downloadedBytes,
            String clientIp,
            String userAgent,
            String errorCode) {
        this.tokenHash = tokenHash;
        this.attachmentId = attachmentId;
        this.objectType = objectType;
        this.objectId = objectId;
        this.linkType = linkType;
        this.requestedAt = requestedAt;
        this.result = result;
        this.httpStatus = httpStatus;
        this.downloadedBytes = downloadedBytes;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.errorCode = errorCode;
    }

    public String tokenHash() { return tokenHash; }

    public Long attachmentId() { return attachmentId; }

    public Integer objectType() { return objectType; }

    public Long objectId() { return objectId; }

    public String linkType() { return linkType; }

    public Instant requestedAt() { return requestedAt; }

    public AttachmentDownloadAuditResult result() { return result; }

    public int httpStatus() { return httpStatus; }

    public Long downloadedBytes() { return downloadedBytes; }

    public String clientIp() { return clientIp; }

    public String userAgent() { return userAgent; }

    public String errorCode() { return errorCode; }

}
