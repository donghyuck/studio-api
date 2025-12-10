package studio.one.platform.exception;

import lombok.Getter;
import studio.one.platform.error.ErrorType;
import studio.one.platform.error.Severity;

/**
 * Unchecked counterpart to {@link PlatformException} that carries {@link ErrorType},
 * optional severity override, and message arguments.
 */
@Getter
public class PlatformRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorType type;
    private final Object[] args;
    private final Severity overrideSeverity; // Use type.severity() if null

    public PlatformRuntimeException(ErrorType type, String logMessage, Object... args) {
        this(type, null, logMessage, args);
    }

    public PlatformRuntimeException(ErrorType type, Severity overrideSeverity, String logMessage, Object... args) {
        super(logMessage);
        this.type = type;
        this.args = args;
        this.overrideSeverity = overrideSeverity;
    }

    public Severity severity() {
        return overrideSeverity != null ? overrideSeverity : type.severity();
    }

    public static PlatformRuntimeException of(ErrorType type, Object... args) {
        return new PlatformRuntimeException(type, type.getId(), args);
    }

    public static PlatformRuntimeException of(ErrorType type, Severity severity, Object... args) {
        return new PlatformRuntimeException(type, severity, type.getId(), args);
    }
}
