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
 *      @file PropertiesRefreshedEvent.java
 *      @date 2025
 *
 */
package studio.echo.platform.component.event;
import org.springframework.context.ApplicationEvent;

/**
 * An {@link ApplicationEvent} that is published when application properties are
 * refreshed.
 *
 * @author donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 */
public class PropertiesRefreshedEvent extends ApplicationEvent {

	private String name;
	
	/**
	 * Creates a new {@code PropertiesRefreshedEvent}.
	 *
	 * @param source the component that published the event
	 * @param name   the name of the properties that were refreshed
	 */
	public PropertiesRefreshedEvent(Object source, String name) {
		super(source);
		this.name = name;
	}

	
	/**
	 * Returns the name of the properties that were refreshed.
	 *
	 * @return the name of the properties
	 */
	public String getName() {
		return name;
	}


	/**
	 * Sets the name of the properties that were refreshed.
	 *
	 * @param name the name of the properties
	 */
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "PropertiesRefreshedEvent [ " + name  + "]";
	}
}

