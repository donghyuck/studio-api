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
 *      @file AlreadyExistsException.java
 *      @date 2025
 *
 */


package studio.echo.platform.exception;

import studio.echo.platform.error.ErrorType;
import studio.echo.platform.error.PlatformErrors;

/**
 * 이미 존재하는 객체에 대한 예외 클래스.
 * 
 * @author  donghyuck, son
 * @since 2025-08-22
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-22  donghyuck, son: 최초 생성.
 * </pre>
 */


public class AlreadyExistsException extends PlatformException {

    public AlreadyExistsException(String message, Object... args) {
        super(PlatformErrors.OBJECT_ALREADY_EXISTS, message, args);
    }

    public AlreadyExistsException(String message, String what, Object id) {
        super(PlatformErrors.OBJECT_ALREADY_EXISTS, message, what, id);
    }

    public AlreadyExistsException(ErrorType type, String message, Object... args) {
        super(type, message, args);
    }

    public static AlreadyExistsException of(String what, Object id) {
        return new AlreadyExistsException("Already exists", what, id);
    }

}
