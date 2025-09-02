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
 

public class StateChangeEvent extends ApplicationEvent {

	private State oldState;

	private State newState;

	public StateChangeEvent(Object source, State oldState, State newState) {
		super(source);
		this.oldState = oldState;
		this.newState = newState;
	}

	public State getNewState() {
		return newState;
	}

	public State getOldState() {
		return oldState;
	}

	@Override
	public String toString() {
		return "StateChangeEvent [oldState=" + oldState + ", newState=" + newState + "]";
	}

}
