package  studio.api.platform.exception;

public class ConfigurationWarning extends RuntimeException {

	public ConfigurationWarning() {
		super();
	}

	public ConfigurationWarning(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public ConfigurationWarning(String message, Throwable cause) {
		super(message, cause);
	}

	public ConfigurationWarning(String message) {
		super(message);
	}

	public ConfigurationWarning(Throwable cause) {
		super(cause);
	}

}
