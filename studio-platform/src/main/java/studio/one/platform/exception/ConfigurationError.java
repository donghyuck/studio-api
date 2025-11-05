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
 *      @file ConfigurationError.java
 *      @date 2025
 *
 */


package studio.one.platform.exception;

import studio.one.platform.error.PlatformErrors;
/**
 * An exception that represents a fatal configuration problem.
 *
 * @author  donghyuck, son
 * @since 2025-08-12
 * @version 1.0
 */
public class ConfigurationError extends PlatformException {

    /**
     * Creates a new {@code ConfigurationError} with the specified message.
     *
     * @param message the detail message
     */
    public ConfigurationError(String message) {
        super(PlatformErrors.CONFIG_INVALID, message);
    }

    /**
     * Creates a new {@code ConfigurationError} with the specified message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the error
     */
    public ConfigurationError(String message, Throwable cause) {
        super(PlatformErrors.CONFIG_INVALID, message, cause);
    }

    /**
     * Creates a new {@code ConfigurationError} for a missing configuration property.
     *
     * @param propertyName the name of the missing property
     * @return a new {@code ConfigurationError} instance
     */
    public static ConfigurationError missing(String propertyName) {
        return new ConfigurationError("Missing required configuration: " + propertyName);
    }

    /**
     * Creates a new {@code ConfigurationError} for an invalid configuration property.
     *
     * @param propertyName the name of the invalid property
     * @param details      details about why the property is invalid
     * @return a new {@code ConfigurationError} instance
     */
    public static ConfigurationError invalid(String propertyName, String details) {
        return new ConfigurationError("Invalid configuration for '" + propertyName + "': " + details);
    }

}