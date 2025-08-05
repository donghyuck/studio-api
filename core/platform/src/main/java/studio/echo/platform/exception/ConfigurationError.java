package studio.echo.platform.exception;

import org.springframework.http.HttpStatus;

import studio.echo.platform.constant.MessageCodes;

public class ConfigurationError extends PlatformException {

    public ConfigurationError(String message) {
        super(MessageCodes.Error.CONFIG_INVALID, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public ConfigurationError(String message, Throwable cause) {
        super(MessageCodes.Error.CONFIG_INVALID, HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }

    public static ConfigurationError missing(String propertyName) {
        return new ConfigurationError("Missing required configuration: " + propertyName);
    }

    public static ConfigurationError invalid(String propertyName, String details) {
        return new ConfigurationError("Invalid configuration for '" + propertyName + "': " + details);
    }

}