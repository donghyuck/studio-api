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
 *      @file State.java
 *      @date 2025
 *
 */
package studio.echo.platform.component;

/**
 * Represents the lifecycle state of a component.
 */
public enum State {
    /**
     * The component has not been initialized.
     */
    NONE("NONE"),
    /**
     * The component is being initialized.
     */
    INITIALIZING("INITIALIZING"),
    /**
     * The component has been initialized.
     */
    INITIALIZED("INITIALIZED"),
    /**
     * The component is starting.
     */
    STARTING("STARTING"),
    /**
     * The component has started.
     */
    STARTED("STARTED"),
    /**
     * The component is stopping.
     */
    STOPPING("STOPPING"),
    /**
     * The component is running.
     */
    RUNNING("RUNNING"),
    /**
     * The component has been stopped.
     */
    STOPPED("STOPPED"),
    /**
     * The component is being destroyed.
     */
    DESTROYING("DESTROYING"),
    /**
     * The component has been destroyed.
     */
    DESTROYED("DESTROYED"),
    /**
     * The component is being created.
     */
    CREATING("CREATING"),
    /**
     * The component has been created.
     */
    CREATED("CREATED"),

    /**
     * The post-upgrade process has started.
     */
    POST_UPGRADE_STARTED("POST_UPGRADE_STARTED");

    private String desc;

    State(String desc) {
        this.desc = desc;
    }

    @Override
    public String toString() {
        return desc;
    }
}
