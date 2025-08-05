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

    public PropertyChangeEvent(Object source, EventType type, String propertyName, Map<String, Object> params) {
        super(source);
        this.eventType = type;
        this.propertyName = propertyName;
        this.params = params;
    }

    public PropertyChangeEvent(Object source, EventType type, String propertyName) {
        super(source);
        this.eventType = type;
        this.propertyName = propertyName;
        this.params = Collections.emptyMap();
    }
    
    public PropertyChangeEvent(Object source, String propertyName, Object oldValue, Object newValue) {
		super(source);
		this.propertyName = propertyName;
		this.newValue = newValue;
		this.oldValue = oldValue;
		this.eventType = EventType.NONE;
	}

	public PropertyChangeEvent(Object source, EventType eventType, String propertyName, Object oldValue, Object newValue) {
		super(source);
		this.propertyName = propertyName;
		this.newValue = newValue;
		this.oldValue = oldValue;
		this.eventType = eventType;
	}
    

    public Object getNewValue() {
        return newValue;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public EventType getEventType() {
        return eventType;
    }

    /**
     * Represents valid event types.
     */
    public enum EventType {

        NONE, 
        /**
         * A property was set.
         */
        PROPERTY_SET,

        /**
         * A property was deleted.
         */
        PROPERTY_DELETEED,

        /**
         * An XML property was set.
         */
        XML_PROPERTY_SET,

        /**
         * An XML property was deleted.
         */
        XML_PROPERTY_DELETEED;
    }

	
}


