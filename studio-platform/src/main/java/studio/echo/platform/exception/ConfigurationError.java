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


package studio.echo.platform.exception;

import studio.echo.platform.error.PlatformErrors;
/**
 *
 * @author  donghyuck, son
 * @since 2025-08-12
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-12  donghyuck, son: 최초 생성.
 * </pre>
 */


public class ConfigurationError extends PlatformException {

    public ConfigurationError(String message) {
        super(PlatformErrors.CONFIG_INVALID, message);
    }

    public ConfigurationError(String message, Throwable cause) {
        super(PlatformErrors.CONFIG_INVALID, message, cause);
    }

    public static ConfigurationError missing(String propertyName) {
        return new ConfigurationError("Missing required configuration: " + propertyName);
    }

    public static ConfigurationError invalid(String propertyName, String details) {
        return new ConfigurationError("Invalid configuration for '" + propertyName + "': " + details);
    }

}