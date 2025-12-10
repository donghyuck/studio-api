package studio.one.platform.text.extractor;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;
import studio.one.platform.exception.PlatformRuntimeException;

/**
 * Raised when file content cannot be parsed.
 */
public class FileParseException extends PlatformRuntimeException {

    private static final long serialVersionUID = 1L;

    private static final ErrorType TYPE = ErrorType.of("error.text.file.parse", HttpStatus.INTERNAL_SERVER_ERROR );

    public FileParseException(String message, Object... args) {
        super(TYPE, message, args);
    }

    public FileParseException(String message, Throwable cause, Object... args) {
        super(TYPE, null, message, args);
        initCause(cause);
    }

    public static FileParseException of(Object... args) {
        return new FileParseException(TYPE.getId(), args);
    }
}
