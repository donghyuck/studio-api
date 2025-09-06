/* 
 *  
 *      Copyright 2023 donghyuck.son
 *  
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *  
 *         http://www.apache.org/licenses/LICENSE-2.0
 *  
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *  
 *  
 */


package studio.echo.platform.component.event;

/**
 * Represents the type of change in an event.
 *
 * @author donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 */
public enum Type {
 
    /**
     * An item was added.
     */
    ADDED,
 
    /**
     * An item was removed.
     */
    REMOVED,
  
    /**
     * An item was modified.
     */
    MODIFIED,

    /**
     * No change.
     */
    NONE
}
