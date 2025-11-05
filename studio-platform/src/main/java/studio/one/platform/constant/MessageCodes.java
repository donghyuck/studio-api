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
 *      @file MessageCodes.java
 *      @date 2025
 *
 */
package studio.one.platform.constant;

import lombok.NoArgsConstructor;

/**
 * A utility class that contains constants for message bundle keys used for
 * internationalization (i18n). This class cannot be instantiated and contains
 * only static inner classes for different message categories.
 *
 * @author donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class MessageCodes {

    /**
     * Contains message codes for informational messages.
     */
    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Info {
        public static final String CONFIG_ROOT_ATTEMPT = "info.config.root.attempt";
        public static final String COMPONENT_STATE = "info.component.state";
        public static final String CONFIG_SOURCE_ENVIRONMENT = "info.config.source.environment";
        public static final String CONFIG_SOURCE_IMPORT = "info.config.source.import";
        public static final String CONFIG_SOURCE_PROPERTY= "info.config.source.property";
        public static final String CONFIG_APPLICATION_HOME_ATTEMPT = "info.config.application.home.attempt";
        public static final String CONFIG_APPLICATION_HOME_ATTEMPT_SERVLET = "info.config.application.home.attempt.servlet";
        public static final String CONFIG_APPLICATION_HOME_SET = "info.config.application.home.set";
        public static final String CONFIG_APPLICATION_HOME_NOT_SET = "info.config.application.home.not.set";
        public static final String CONFIG_APPLICATION_HOME_EXISTS = "info.config.application.home.exists" ;
        
    }

    /**
     * Contains message codes for error messages.
     */
    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Error {
        public static final String CONFIG_ROOT_NULL = "error.config.root.null"; 
        public static final String CONFIG_ROOT_INVALID = "error.config.root.invalid"; 
        public static final String CONFIG_IO = "error.config.io";
        public static final String CONFIG_UNKNOWN = "error.config.unknown";
         public static final String CONFIG_INVALID = "error.config.invalid";
        public static final String NOT_FOUND = "error.not.found";
        public static final String OBJECT_NOT_FOUND = "error.not.found.object";
        public static final String FILE_ACCESS = "error.file.access";
        public static final String CONFIG_ROOT_NOT_INITIALIZED ="error.config.root.not.initialized";
        public static final String CONFIG_APPLICATION_HOME_FAILED =  "error.config.application.home.failed";
    }

    /**
     * Contains message codes for warning messages.
     */
    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Warn {
        public static final String CONFIG = "warn.config";
        public static final String CONFIG_DEPRECATED = "warn.config.deprecated";
        public static final String CONFIG_FALLBACK = "warn.config.fallback";
        public static final String MANIFEST_READ = "warn.manifest.read";
        public static final String LOGO_READ = "warn.logo.read";
        public static final String CONFIG_ALREADY_INITIALIZED = "warn.config.already.initialized";
    }

}
