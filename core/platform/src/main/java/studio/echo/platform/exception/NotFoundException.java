package studio.echo.platform.exception;

import org.springframework.http.HttpStatus;

import studio.echo.platform.constant.MessageCodes;

public class NotFoundException extends PlatformException {
    public NotFoundException(String message, Object... args) {
        super(MessageCodes.Error.NOT_FOUND, HttpStatus.NOT_FOUND, message, args);
    }
}

