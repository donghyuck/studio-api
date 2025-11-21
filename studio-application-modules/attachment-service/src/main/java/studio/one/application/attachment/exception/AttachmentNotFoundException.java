package studio.one.application.attachment.exception;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;
import studio.one.platform.exception.NotFoundException;

public class AttachmentNotFoundException extends NotFoundException {

	private static final ErrorType BY_ID = ErrorType.of("error.attachment.not.found.id", HttpStatus.NOT_FOUND);
	private static final ErrorType BY_NAME = ErrorType.of("error.attachment.not.found.name", HttpStatus.NOT_FOUND);

	public AttachmentNotFoundException(Long attachmentId) {
		super(BY_ID, "Attachment Not Found", attachmentId);
	}

	public AttachmentNotFoundException(String filename) {
		super(BY_NAME, "Attachment Not Found", filename);
	}

	public static AttachmentNotFoundException byId(Long attachmentId) {
		return new AttachmentNotFoundException(attachmentId);
	}

	public static AttachmentNotFoundException byName(String filename) {
		return new AttachmentNotFoundException(filename);
	}
}
