package studio.one.application.attachment.domain.model;

public class AttachmentDownloadAuditLogCount {

    private final Long issueLogId;
    private final String tokenHash;
    private final long count;

    public AttachmentDownloadAuditLogCount(
            Long issueLogId,
            String tokenHash,
            long count) {
        this.issueLogId = issueLogId;
        this.tokenHash = tokenHash;
        this.count = count;
    }

    public Long issueLogId() { return issueLogId; }

    public String tokenHash() { return tokenHash; }

    public long count() { return count; }

}
