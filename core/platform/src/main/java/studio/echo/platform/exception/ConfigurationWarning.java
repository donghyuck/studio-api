package studio.echo.platform.exception;

import org.springframework.http.HttpStatus;

import studio.echo.platform.constant.MessageCodes;

public class ConfigurationWarning extends PlatformException {

    public ConfigurationWarning(String message) {
        super(MessageCodes.Warn.CONFIG, HttpStatus.OK, message);
    }

    public ConfigurationWarning(String message, Object... args) {
        super(MessageCodes.Warn.CONFIG, HttpStatus.OK, message, args);
    }

    public static ConfigurationWarning deprecated(String property) {
        return new ConfigurationWarning("Deprecated configuration in use: " + property);
    }

    public static ConfigurationWarning fallback(String property, String defaultValue) {
        return new ConfigurationWarning("Configuration '" + property + "' is missing. Using default: " + defaultValue);
    }
}
