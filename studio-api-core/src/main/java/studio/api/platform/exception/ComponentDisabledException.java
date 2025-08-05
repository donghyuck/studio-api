package  studio.api.platform.exception;

/**
 * ComponentDisabledException is a custom exception class that extends RuntimeException.
 * It is thrown when a component is disabled and cannot be used.
 * This exception can be used to indicate that a specific functionality or feature is not available
 * due to the component being disabled, providing a clear message to the user or developer.
 * It supports various constructors to allow for detailed error messages and cause chaining.
 * 
 * @author  donghyuck, son
 * @since 2025-07-08
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-08  donghyuck, son: 최초 생성.
 * </pre>
 */
public class ComponentDisabledException extends RuntimeException {

	public ComponentDisabledException() {
		super();
	}

	public ComponentDisabledException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace); 
	}

	public ComponentDisabledException(String message, Throwable cause) {
		super(message, cause);
	}

	public ComponentDisabledException(String message) {
		super(message);
	}

	public ComponentDisabledException(Throwable cause) {
		super(cause);
	}

}
