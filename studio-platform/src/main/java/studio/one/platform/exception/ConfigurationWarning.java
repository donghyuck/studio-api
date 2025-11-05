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
 *      @file ConfigurationWarning.java
 *      @date 2025
 *
 */


package studio.one.platform.exception;

import studio.one.platform.error.PlatformErrors;
/**
 * An exception that represents a non-fatal configuration problem.
 *
 * @author  donghyuck, son
 * @since 2025-08-12
 * @version 1.0
 */
public class ConfigurationWarning extends PlatformException {

    /**
     * Creates a new {@code ConfigurationWarning} with the specified message.
     *
     * @param message the detail message
     */
    public ConfigurationWarning(String message) {
        super(PlatformErrors.CONFIG, message);
    }

    /**
     * Creates a new {@code ConfigurationWarning} with the specified message and
     * arguments.
     *
     * @param message the detail message
     * @param args    arguments for the message
     */
    public ConfigurationWarning(String message, Object... args) {
        super(PlatformErrors.CONFIG, message, args);
    }

    /**
     * Creates a new {@code ConfigurationWarning} for a deprecated configuration
     * property.
     *
     * @param property the name of the deprecated property
     * @return a new {@code ConfigurationWarning} instance
     */
    public static ConfigurationWarning deprecated(String property) {
        return new ConfigurationWarning("Deprecated configuration in use: " + property);
    }

    /**
     * Creates a new {@code ConfigurationWarning} for a configuration property that
     * is using a fallback value.
     *
     * @param property     the name of the property
     * @param defaultValue the fallback value being used
     * @return a new {@code ConfigurationWarning} instance
     */
    public static ConfigurationWarning fallback(String property, String defaultValue) {
        return new ConfigurationWarning("Configuration '" + property + "' is missing. Using default: " + defaultValue);
    }
}
