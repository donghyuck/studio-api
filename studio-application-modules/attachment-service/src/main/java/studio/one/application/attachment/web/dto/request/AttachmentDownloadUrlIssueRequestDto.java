package studio.one.application.attachment.web.dto.request;

public class AttachmentDownloadUrlIssueRequestDto {

    private Long ttlSeconds;

    public AttachmentDownloadUrlIssueRequestDto() {
    }

    public AttachmentDownloadUrlIssueRequestDto(
            Long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public Long getTtlSeconds() { return ttlSeconds; }

    public Long ttlSeconds() { return ttlSeconds; }

    public void setTtlSeconds(Long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
}
