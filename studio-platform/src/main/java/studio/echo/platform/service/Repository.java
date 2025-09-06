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
 *      @file Repository.java
 *      @date 2025
 *
 */
package studio.echo.platform.service;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import studio.echo.platform.exception.NotFoundException;
 

/**
 * An interface that provides access to the application's configuration and
 * resources.
 *
 * @author  donghyuck, son
 * @since 2025-07-08
 * @version 1.0
 */
public interface Repository {

    /**
     * Returns the root of the configuration directory.
     *
     * @return the configuration root
     * @throws NotFoundException if the configuration root cannot be found
     */
    ConfigRoot getConfigRoot() throws NotFoundException;
    
    /**
     * Returns a file from the application's root directory by its name.
     *
     * @param name the name of the file
     * @return the file
     * @throws IOException if the file cannot be accessed
     */
    File getFile(String name) throws IOException;
    
    /**
     * Returns the application properties.
     *
     * @return the application properties
     */
    ApplicationProperties getApplicationProperties();

    /**
     * Returns the uptime of the application.
     *
     * @return the application uptime
     */
    Duration getUptime() ;
}
