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

public enum State {
    /**
     */
    NONE("NONE"),
    /**
     * 컴포넌트가 생성 중
     */
    INITIALIZING("INITIALIZING"),
    /**
     * 컴포넌트가 초기화됨
     */
    INITIALIZED("INITIALIZED"),
    /**
     * 컴포넌트가 시작 중
     */
    STARTING("STARTING"),
    /**
     * 컴포넌트가 시작됨
     */
    STARTED("STARTED"),
    /**
     * 컴포넌트가 중지 중
     */
    STOPING("STOPING"),
    /**
     * 컴포넌트가 실행 중
     */
    RUNNING("RUNNING"),
    /**
     *  컴포넌트가 중지 중
     */
    STOPED("STOPED"),
    /**
     * 컴포넌트가 파괴 중
     */
    DESTROYING("DESTROYING"),
    /**
     * 컴포넌트가 파괴됨
     */
    DESTROYED("DESTROYED"),

    CREATING("CREATING"),
    /**
     * 컴포넌트 생성됨
     */
    CREATED("CREATED"),

    /**
     * 업그레이드 시작
     */
    POST_UPGRADE_STARTED("POST_UPGRADE_STARTED");

    private String desc;

    private State(String desc) {
        this.desc = desc;
    }

    @Override
    public String toString() {
        return desc;
    }
}
