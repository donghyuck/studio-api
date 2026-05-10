package studio.one.application.attachment.application.error;

public class AttachmentDownloadTokenInvalidException extends RuntimeException {

    public AttachmentDownloadTokenInvalidException() {
        super("Attachment download token is invalid");
    }
}
