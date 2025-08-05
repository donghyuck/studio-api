package  studio.api.platform.exception;

public class RuntimeError extends Error {

	public RuntimeError() {
		super(); 
	}

	public RuntimeError(String message) {
		super(message); 
	}

	public RuntimeError(String message, Throwable cause) {
		super(message, cause); 
	}

	public RuntimeError(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace); 
	}

	public RuntimeError(Throwable cause) {
		super(cause); 
	}

}
