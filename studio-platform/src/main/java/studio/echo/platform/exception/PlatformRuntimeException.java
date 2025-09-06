/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file PlatformException.java
 *      @date 2025
 *
 */
package studio.echo.platform.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * The abstract base class for all platform runtime exceptions.
 * <p>
 * This class provides a consistent structure for custom exceptions, including
 * an error code, HTTP status, message, and message arguments.
 * 
 * @author donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 */
@Getter
public abstract class PlatformRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The error code or message key. */
    private final String errorCode;

    /** The HTTP status code. */
    private final HttpStatus status;

    /** The arguments to be inserted into the message. */
    private final transient Object[] args;

    /**
     * Creates a new {@code PlatformRuntimeException}.
     *
     * @param errorCode the error code or message key
     * @param status    the HTTP status
     * @param message   the detail message
     */
    protected PlatformRuntimeException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.args = new Object[0];
    }

    /**
     * Creates a new {@code PlatformRuntimeException}.
     *
     * @param errorCode the error code or message key
     * @param status    the HTTP status
     * @param message   the detail message
     * @param cause     the cause of the exception
     */
    protected PlatformRuntimeException(String errorCode, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
        this.args = new Object[0];
    }

    /**
     * Creates a new {@code PlatformRuntimeException}.
     *
     * @param errorCode the error code or message key
     * @param status    the HTTP status
     * @param message   the detail message
     * @param args      the arguments for the message
     */
    protected PlatformRuntimeException(String errorCode, HttpStatus status, String message, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.args = args;
    }

    /**
     * Creates a new {@code PlatformRuntimeException}.
     *
     * @param errorCode the error code or message key
     * @param status    the HTTP status
     * @param message   the detail message
     * @param cause     the cause of the exception
     * @param args      the arguments for the message
     */
    protected PlatformRuntimeException(String errorCode, HttpStatus status, String message, Throwable cause,
            Object... args) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
        this.args = args;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Appends the nested exception message if available.
     */
    @Override
    public String getMessage() {
        String message = super.getMessage();
        Throwable cause = getCause();
        if (cause != null && cause != this) {
            return message + "; nested exception is " + cause;
        }
        return message;
    }

    /**
     * Returns the root cause of this exception.
     *
     * @return the root cause, or {@code null} if there is no cause
     */
    public Throwable getRootCause() {
        Throwable root = this;
        Throwable cause = getCause();
        while (cause != null && cause != root) {
            root = cause;
            cause = cause.getCause();
        }
        return root == this ? null : root;
    }

    /**
     * Returns the most specific cause of this exception.
     * <p>
     * Returns the root cause if available, otherwise returns this exception itself.
     *
     * @return the most specific cause
     */
    public Throwable getMostSpecificCause() {
        Throwable rootCause = getRootCause();
        return (rootCause != null ? rootCause : this);
    }

    /**
     * Checks if this exception or any of its causes is of the specified type.
     *
     * @param exType the type of exception to check for
     * @return {@code true} if the exception chain contains the specified type,
     *         {@code false} otherwise
     */
    public boolean contains(Class<?> exType) {
        if (exType == null) {
            return false;
        }
        if (exType.isInstance(this)) {
            return true;
        }
        Throwable cause = getCause();
        if (cause == this) {
            return false;
        }
        while (cause != null) {
            if (exType.isInstance(cause)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        return false;
    }

}
