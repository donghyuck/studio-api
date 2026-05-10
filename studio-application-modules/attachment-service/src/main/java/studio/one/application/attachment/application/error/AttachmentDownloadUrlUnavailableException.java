package studio.one.application.attachment.application.error;

public class AttachmentDownloadUrlUnavailableException extends RuntimeException {

    public AttachmentDownloadUrlUnavailableException() {
        super("Attachment download URL is not available");
    }
}
