package studio.one.platform.objecttype.error;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;
import studio.one.platform.error.Severity;

public enum ObjectTypeErrorCodes implements ErrorType {
    UNKNOWN_OBJECT_TYPE(HttpStatus.NOT_FOUND),
    OBJECT_TYPE_DISABLED(HttpStatus.CONFLICT),
    OBJECT_TYPE_DEPRECATED(HttpStatus.CONFLICT),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    POLICY_VIOLATION(HttpStatus.BAD_REQUEST),
    CONFLICT(HttpStatus.CONFLICT);

    private final HttpStatus status;

    ObjectTypeErrorCodes(HttpStatus status) {
        this.status = status;
    }

    @Override
    public String getId() {
        return "objecttype." + name();
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public Severity getSeverity() {
        return Severity.ERROR;
    }
}
