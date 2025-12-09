package studio.one.base.security.exception;

import org.springframework.http.HttpStatus;

import studio.one.platform.exception.PlatformRuntimeException;

public class PasswordResetException extends PlatformRuntimeException {

    protected PasswordResetException(String errorCode, HttpStatus status, String message, Object[] args) {
        super(errorCode, status, message, args); 
    }

}
