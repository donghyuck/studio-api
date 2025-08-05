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
 *      @file PlatformException.java
 *      @date 2025
 *
 */
package studio.echo.platform.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * 플랫폼 기반 예외의 최상위 추상 클래스.
 * 모든 커스텀 예외는 이 클래스를 확장하여 일관된 구조를 따름.
 * 
 * @author  donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-21  donghyuck, son: 최초 생성.
 * </pre>
 */


@Getter
public abstract class PlatformException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 메시지 키 또는 에러 코드 */
    private final String errorCode;

    /** HTTP 상태 코드 */
    private final HttpStatus status;

    /** 메시지에 삽입될 변수들 */
    private final transient Object[] args;

    /**
     * 기본 생성자
     */
    protected PlatformException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.args = new Object[0];
    }

    protected PlatformException(String errorCode, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
        this.args = new Object[0];
    }

    protected PlatformException(String errorCode, HttpStatus status, String message, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.args = args;
    }

    protected PlatformException(String errorCode, HttpStatus status, String message, Throwable cause, Object... args) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
        this.args = args;
    }
}
