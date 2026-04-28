package studio.one.platform.text.extractor;

import studio.one.platform.error.ErrorType;

/**
 * @deprecated since 2026-04-20. Use
 *             {@link studio.one.platform.textract.extractor.FileParseException}.
 */
@Deprecated(forRemoval = false)
public class FileParseException extends studio.one.platform.textract.extractor.FileParseException {

    private static final long serialVersionUID = 1L;

    public FileParseException(String message, Object... args) {
        super(message, args);
    }

    public FileParseException(String message, Throwable cause, Object... args) {
        super(message, cause, args);
    }

    public FileParseException(ErrorType type, String message, Throwable cause, Object... args) {
        super(type, message, args);
        initCause(cause);
    }

    public static FileParseException of(Object... args) {
        return new FileParseException("error.text.file.parse", args);
    }

    public static FileParseException from(studio.one.platform.textract.extractor.FileParseException exception) {
        if (exception instanceof FileParseException legacy) {
            return legacy;
        }
        return new FileParseException(exception.getType(), exception.getMessage(), exception, exception.getArgs());
    }
}
