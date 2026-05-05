package studio.one.application.attachment.exception;

public class AttachmentDownloadTokenInvalidException extends RuntimeException {

    public AttachmentDownloadTokenInvalidException() {
        super("Attachment download token is invalid");
    }
}
