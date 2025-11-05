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
 *      @file Severity.java
 *      @date 2025
 *
 */


package studio.one.platform.error;
/**
 * Represents the severity of an error.
 * 
 * @author  donghyuck, son
 * @since 2025-08-12
 * @version 1.0
 */
public enum Severity {
    /**
     * Informational message, typically for flow guidance or minor business rules.
     * Often associated with 2xx/4xx HTTP statuses.
     */
    INFO,
    /**
     * A warning about a potential issue or constraint.
     * Often associated with 4xx HTTP statuses.
     */
    WARN,
    /**
     * A system or unexpected error.
     * Often associated with 5xx HTTP statuses.
     */
    ERROR
}
