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
 *      @file StateChangeEvent.java
 *      @date 2025
 *
 */


package studio.echo.platform.component.event;

import org.springframework.context.ApplicationEvent;

import studio.echo.platform.component.State;
 
/**
 * An {@link ApplicationEvent} that is published when a component's state
 * changes.
 *
 * @author donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 */
public class StateChangeEvent extends ApplicationEvent {

	private State oldState;

	private State newState;

	/**
	 * Creates a new {@code StateChangeEvent}.
	 *
	 * @param source   the component that published the event
	 * @param oldState the old state of the component
	 * @param newState the new state of the component
	 */
	public StateChangeEvent(Object source, State oldState, State newState) {
		super(source);
		this.oldState = oldState;
		this.newState = newState;
	}

	/**
	 * Returns the new state of the component.
	 *
	 * @return the new state
	 */
	public State getNewState() {
		return newState;
	}

	/**
	 * Returns the old state of the component.
	 *
	 * @return the old state
	 */
	public State getOldState() {
		return oldState;
	}

	@Override
	public String toString() {
		return "StateChangeEvent [oldState=" + oldState + ", newState=" + newState + "]";
	}

}
