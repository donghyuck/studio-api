package studio.one.application.attachment.web.dto.response;

import java.time.Instant;

import studio.one.application.attachment.domain.model.AttachmentDownloadAuditLog;

public class AttachmentDownloadAuditLogDto {

    private final Long downloadLogId;
    private final Long issueLogId;
    private final String tokenHash;
    private final Long attachmentId;
    private final Integer objectType;
    private final Long objectId;
    private final String linkType;
    private final Instant requestedAt;
    private final String result;
    private final Integer httpStatus;
    private final Long downloadedBytes;
    private final String clientIp;
    private final String userAgent;
    private final String errorCode;

    public AttachmentDownloadAuditLogDto(
            Long downloadLogId,
            Long issueLogId,
            String tokenHash,
            Long attachmentId,
            Integer objectType,
            Long objectId,
            String linkType,
            Instant requestedAt,
            String result,
            Integer httpStatus,
            Long downloadedBytes,
            String clientIp,
            String userAgent,
            String errorCode) {
        this.downloadLogId = downloadLogId;
        this.issueLogId = issueLogId;
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

    public Long downloadLogId() { return downloadLogId; }

    public Long issueLogId() { return issueLogId; }

    public String tokenHash() { return tokenHash; }

    public Long attachmentId() { return attachmentId; }

    public Integer objectType() { return objectType; }

    public Long objectId() { return objectId; }

    public String linkType() { return linkType; }

    public Instant requestedAt() { return requestedAt; }

    public String result() { return result; }

    public Integer httpStatus() { return httpStatus; }

    public Long downloadedBytes() { return downloadedBytes; }

    public String clientIp() { return clientIp; }

    public String userAgent() { return userAgent; }

    public String errorCode() { return errorCode; }

public static AttachmentDownloadAuditLogDto from(AttachmentDownloadAuditLog log) {
        return new AttachmentDownloadAuditLogDto(
                log.getDownloadLogId(),
                log.getIssueLogId(),
                log.getTokenHash(),
                log.getAttachmentId(),
                log.getObjectType(),
                log.getObjectId(),
                log.getLinkType(),
                log.getRequestedAt(),
                log.getResult(),
                log.getHttpStatus(),
                log.getDownloadedBytes(),
                log.getClientIp(),
                log.getUserAgent(),
                log.getErrorCode());
    }
    public Long getDownloadLogId() { return downloadLogId; }

    public Long getIssueLogId() { return issueLogId; }

    public String getTokenHash() { return tokenHash; }

    public Long getAttachmentId() { return attachmentId; }

    public Integer getObjectType() { return objectType; }

    public Long getObjectId() { return objectId; }

    public String getLinkType() { return linkType; }

    public Instant getRequestedAt() { return requestedAt; }

    public String getResult() { return result; }

    public Integer getHttpStatus() { return httpStatus; }

    public Long getDownloadedBytes() { return downloadedBytes; }

    public String getClientIp() { return clientIp; }

    public String getUserAgent() { return userAgent; }

    public String getErrorCode() { return errorCode; }

}
