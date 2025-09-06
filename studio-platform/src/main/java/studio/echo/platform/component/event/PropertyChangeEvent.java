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
 *      @file PropertyChangeEvent.java
 *      @date 2025
 *
 */
package studio.echo.platform.component.event;


import java.util.Collections;
import java.util.Map;

import org.springframework.context.ApplicationEvent;

/**
 * An {@link ApplicationEvent} that is published when a property changes.
 *
 * @author donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 */
public class PropertyChangeEvent extends ApplicationEvent  {

    private String propertyName;
    private transient Map<String, Object> params;
    private EventType eventType;

	/**
	 * New value for property. May be null if not known.
	 */
	private transient Object newValue;

	/**
	 * Previous value for property. May be null if not known.
	*/
     private transient Object oldValue;

    /**
     * Creates a new {@code PropertyChangeEvent}.
     *
     * @param source       the component that published the event
     * @param type         the type of event
     * @param propertyName the name of the property that changed
     * @param params       additional parameters for the event
     */
    public PropertyChangeEvent(Object source, EventType type, String propertyName, Map<String, Object> params) {
        super(source);
        this.eventType = type;
        this.propertyName = propertyName;
        this.params = params;
    }

    /**
     * Creates a new {@code PropertyChangeEvent}.
     *
     * @param source       the component that published the event
     * @param type         the type of event
     * @param propertyName the name of the property that changed
     */
    public PropertyChangeEvent(Object source, EventType type, String propertyName) {
        super(source);
        this.eventType = type;
        this.propertyName = propertyName;
        this.params = Collections.emptyMap();
    }
    
    /**
     * Creates a new {@code PropertyChangeEvent}.
     *
     * @param source       the component that published the event
     * @param propertyName the name of the property that changed
     * @param oldValue     the old value of the property
     * @param newValue     the new value of the property
     */
    public PropertyChangeEvent(Object source, String propertyName, Object oldValue, Object newValue) {
		super(source);
		this.propertyName = propertyName;
		this.newValue = newValue;
		this.oldValue = oldValue;
		this.eventType = EventType.NONE;
	}

    /**
     * Creates a new {@code PropertyChangeEvent}.
     *
     * @param source       the component that published the event
     * @param eventType    the type of event
     * @param propertyName the name of the property that changed
     * @param oldValue     the old value of the property
     * @param newValue     the new value of the property
     */
	public PropertyChangeEvent(Object source, EventType eventType, String propertyName, Object oldValue, Object newValue) {
		super(source);
		this.propertyName = propertyName;
		this.newValue = newValue;
		this.oldValue = oldValue;
		this.eventType = eventType;
	}
    

    /**
     * Returns the new value of the property.
     *
     * @return the new value
     */
    public Object getNewValue() {
        return newValue;
    }

    /**
     * Returns the old value of the property.
     *
     * @return the old value
     */
    public Object getOldValue() {
        return oldValue;
    }

    /**
     * Returns the name of the property that changed.
     *
     * @return the property name
     */
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * Returns additional parameters for the event.
     *
     * @return the event parameters
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * Returns the type of event.
     *
     * @return the event type
     */
    public EventType getEventType() {
        return eventType;
    }

    /**
     * Represents valid event types for property changes.
     */
    public enum EventType {

        /**
         * No specific event type.
         */
        NONE, 
        /**
         * A property was set.
         */
        PROPERTY_SET,

        /**
         * A property was deleted.
         */
        PROPERTY_DELETED,

        /**
         * An XML property was set.
         */
        XML_PROPERTY_SET,

        /**
         * An XML property was deleted.
         */
        XML_PROPERTY_DELETED;
    }

	
}


