package studio.one.application.attachment.application.result;

public class AttachmentDownloadUrlIssueActor {

    private final Long userId;
    private final String principalName;

    public AttachmentDownloadUrlIssueActor(
            Long userId,
            String principalName) {
        this.userId = userId;
        this.principalName = principalName;
    }

    public Long userId() { return userId; }

    public String principalName() { return principalName; }

}
