package studio.one.platform.text.extractor;

import org.springframework.http.HttpStatus;

import studio.one.platform.exception.PlatformRuntimeException;

/**
 * Raised when file content cannot be parsed.
 */
public class FileParseException extends PlatformRuntimeException {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_CODE = "error.file.parse";
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.BAD_REQUEST;

    public FileParseException(String message) {
        super(DEFAULT_CODE, DEFAULT_STATUS, message);
    }

    public FileParseException(String message, Throwable cause) {
        super(DEFAULT_CODE, DEFAULT_STATUS, message, cause);
    }
}
