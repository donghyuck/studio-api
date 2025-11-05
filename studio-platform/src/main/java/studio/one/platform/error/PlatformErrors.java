package studio.one.platform.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * An enum that defines a set of standard platform errors, each with an ID,
 * HTTP status, and severity. This enum implements the {@link ErrorType}
 * interface.
 *
 * @author donghyuck, son
 * @since 2025-08-12
 * @version 1.0
 */
@Getter
@RequiredArgsConstructor
public enum PlatformErrors implements ErrorType {

    CONFIG_ROOT_NULL("error.config.root.null", HttpStatus.INTERNAL_SERVER_ERROR, Severity.WARN),
    CONFIG_ROOT_INVALID("error.config.root.invalid", HttpStatus.INTERNAL_SERVER_ERROR, Severity.WARN),
    CONFIG_IO("error.config.io", HttpStatus.INTERNAL_SERVER_ERROR, Severity.WARN),
    CONFIG_UNKNOWN("error.config.unknown", HttpStatus.INTERNAL_SERVER_ERROR, Severity.WARN),
    CONFIG_INVALID("error.config.invalid", HttpStatus.INTERNAL_SERVER_ERROR, Severity.WARN),
    NOT_FOUND("error.not.found", HttpStatus.NOT_FOUND, Severity.WARN),
    OBJECT_NOT_FOUND("error.not.found.object", HttpStatus.NOT_FOUND, Severity.WARN),
    OBJECT_ALREADY_EXISTS("error.already.exists.object", HttpStatus.INTERNAL_SERVER_ERROR, Severity.WARN), 
    FILE_ACCESS("error.file.access", HttpStatus.UNAUTHORIZED, Severity.WARN),
    CONFIG_ROOT_NOT_INITIALIZED("error.config.root.not.initialized", HttpStatus.INTERNAL_SERVER_ERROR, Severity.WARN),
    CONFIG_APPLICATION_HOME_FAILED("error.config.application.home.failed", HttpStatus.INTERNAL_SERVER_ERROR, Severity.WARN),
    

    CONFIG("warn.config", HttpStatus.OK, Severity.WARN),
    CONFIG_DEPRECATED("warn.config.deprecated", HttpStatus.OK, Severity.WARN),
    CONFIG_FALLBACK("warn.config.fallback", HttpStatus.OK, Severity.WARN),
    MANIFEST_READ("warn.manifest.read", HttpStatus.OK, Severity.WARN),
    LOGO_READ("warn.logo.read", HttpStatus.OK, Severity.WARN),
    CONFIG_ALREADY_INITIALIZED("warn.config.already.initialized", HttpStatus.OK, Severity.WARN)
    ;


    
    private final String id;
    private final HttpStatus status;  
    private final Severity severity;

    

}
