package studio.one.platform.textract.extractor;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;

/**
 * Raised when text extraction rejects input before parser dispatch because it
 * exceeds the configured byte limit.
 */
public class FileSizeLimitExceededException extends FileParseException {

    private static final long serialVersionUID = 1L;

    private static final ErrorType TYPE = ErrorType.of("error.text.file.too-large", HttpStatus.PAYLOAD_TOO_LARGE);

    public FileSizeLimitExceededException(String filename, long size, long limit) {
        super(TYPE, "File too large to extract text: " + filename
                + " (observed-size=" + size + " bytes, limit=" + limit
                + " bytes; configure studio.textract.max-extract-size)",
                filename, size, limit);
    }
}
