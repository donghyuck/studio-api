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

import lombok.Getter;
import studio.echo.platform.error.ErrorType;
import studio.echo.platform.error.Severity;
/**
 * The base class for all platform-specific exceptions.
 *
 * @author  donghyuck, son
 * @since 2025-08-12
 * @version 1.0
 */
@Getter
public class PlatformException extends RuntimeException  {
    
    private final ErrorType type;
    private final Object[] args;
    private final Severity overrideSeverity; // Use type.severity() if null

    /**
     * Creates a new {@code PlatformException}.
     *
     * @param type       the type of error
     * @param logMessage the message to log
     * @param args       arguments for the message
     */
    public PlatformException(ErrorType type, String logMessage, Object... args) {
        this(type, null, logMessage, args);
    }
    
    /**
     * Creates a new {@code PlatformException} with an overridden severity.
     *
     * @param type             the type of error
     * @param overrideSeverity the severity to use instead of the default from the
     *                         error type
     * @param logMessage       the message to log
     * @param args             arguments for the message
     */
    public PlatformException(ErrorType type, Severity overrideSeverity, String logMessage, Object... args) {
        super(logMessage);
        this.type = type;
        this.args = args;
        this.overrideSeverity = overrideSeverity;
    }

    /**
     * Returns the severity of the exception.
     *
     * @return the overridden severity, or the default severity from the error type
     */
    public Severity severity() {
        return overrideSeverity != null ? overrideSeverity : type.severity();
    }

    /**
     * Creates a new {@code PlatformException} with the specified error type.
     *
     * @param type the error type
     * @param args arguments for the message
     * @return a new {@code PlatformException} instance
     */
    public static PlatformException of(ErrorType type, Object... args) {
        return new PlatformException(type, type.getId(), args);
    }

    /**
     * Creates a new {@code PlatformException} with the specified error type and
     * severity.
     *
     * @param type     the error type
     * @param severity the severity of the exception
     * @param args     arguments for the message
     * @return a new {@code PlatformException} instance
     */
    public static PlatformException of(ErrorType type, Severity severity, Object... args) {
        return new PlatformException(type, severity, type.getId(), args);
    }
}
