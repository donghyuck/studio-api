package  studio.api.platform.exception;

public class ConfigurationError extends RuntimeException {

	public ConfigurationError() {
		super();
	}

	public ConfigurationError(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public ConfigurationError(String message, Throwable cause) {
		super(message, cause);
	}

	public ConfigurationError(String message) {
		super(message);
	}

	public ConfigurationError(Throwable cause) {
		super(cause);
	}

}
