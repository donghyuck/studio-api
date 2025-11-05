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
 *      @file ApplicationProperties.java
 *      @date 2025
 *
 */
package studio.one.platform.service;

import java.util.Collection;
import java.util.Map;
/**
 * An interface that provides access to application properties. This interface
 * extends {@link Map<String, String>} to provide key-value access to all
 * properties.
 *
 * @author  donghyuck, son
 * @since 2025-07-08
 * @version 1.0
 */
public interface ApplicationProperties extends Map<String, String> {

    /**
     * Returns the value of a boolean property.
     *
     * @param name the name of the property
     * @return the property value, or {@code false} if not found
     */
    boolean getBooleanProperty(String name);

    /**
     * Returns the value of a boolean property, with a default value.
     *
     * @param name         the name of the property
     * @param defaultValue the default value to return if the property is not found
     * @return the property value, or the default value if not found
     */
    boolean getBooleanProperty(String name, boolean defaultValue);

    /**
     * Returns the names of all direct children of a property.
     *
     * @param name the name of the parent property
     * @return a collection of child property names
     */
    Collection<String> getChildrenNames(String name);

    /**
     * Returns the value of an integer property, with a default value.
     *
     * @param name         the name of the property
     * @param defaultValue the default value to return if the property is not found
     * @return the property value, or the default value if not found
     */
    int getIntProperty(String name, int defaultValue);

    /**
     * Returns the value of a long property, with a default value.
     *
     * @param name         the name of the property
     * @param defaultValue the default value to return if the property is not found
     * @return the property value, or the default value if not found
     */
    long getLongProperty(String name, long defaultValue);

    /**
     * Returns the names of all properties.
     *
     * @return a collection of all property names
     */
    Collection<String> getPropertyNames();

    /**
     * Returns the value of a string property, with a default value.
     *
     * @param name         the name of the property
     * @param defaultValue the default value to return if the property is not found
     * @return the property value, or the default value if not found
     */
    String getStringProperty(String name, String defaultValue);
    
}
