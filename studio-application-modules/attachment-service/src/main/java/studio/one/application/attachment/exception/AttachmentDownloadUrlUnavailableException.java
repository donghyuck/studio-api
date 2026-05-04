package studio.one.application.attachment.exception;

public class AttachmentDownloadUrlUnavailableException extends RuntimeException {

    public AttachmentDownloadUrlUnavailableException() {
        super("Attachment download URL is not available");
    }
}
