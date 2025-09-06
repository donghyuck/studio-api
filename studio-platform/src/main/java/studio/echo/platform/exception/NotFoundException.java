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
 *      @file NotFoundException.java
 *      @date 2025
 *
 */

package studio.echo.platform.exception;

import studio.echo.platform.error.ErrorType;
import studio.echo.platform.error.PlatformErrors;

/**
 * An exception that is thrown when an object cannot be found.
 * 
 * @author donghyuck, son
 * @since 2025-08-12
 * @version 1.0
 */
public class NotFoundException extends PlatformException {

    /**
     * Creates a new {@code NotFoundException} with a default error type.
     *
     * @param message the detail message
     * @param args    arguments for the message
     */
    public NotFoundException(String message, Object... args) {
        super(PlatformErrors.NOT_FOUND, message, args);
    }

    /**
     * Creates a new {@code NotFoundException} with a default error type.
     *
     * @param message the detail message
     * @param what    the type of object that was not found
     * @param id      the ID of the object that was not found
     */
    public NotFoundException(String message, String what, Object id) {
        super(PlatformErrors.OBJECT_NOT_FOUND, message, what, id);
    }

    /**
     * Creates a new {@code NotFoundException} with a custom error type.
     *
     * @param type    the error type
     * @param message the detail message
     * @param args    arguments for the message
     */
    public NotFoundException(ErrorType type, String message, Object... args) {
        super(type, message, args);
    }

    /**
     * Creates a new {@code NotFoundException} with a default message.
     *
     * @param what the type of object that was not found
     * @param id   the ID of the object that was not found
     * @return a new {@code NotFoundException} instance
     */
    public static NotFoundException of(String what, Object id) {
        return new NotFoundException("Not found", what, id);
    }
}
