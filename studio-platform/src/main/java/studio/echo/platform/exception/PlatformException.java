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

import lombok.Getter;
import studio.echo.platform.error.ErrorType;
import studio.echo.platform.error.Severity;
/**
 *
 * @author  donghyuck, son
 * @since 2025-08-12
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-12  donghyuck, son: 최초 생성.
 * </pre>
 */


@Getter
public class PlatformException extends RuntimeException  {
    
    private final ErrorType type;
    private final Object[] args;
    private final Severity overrideSeverity; // null이면 type.severity() 사용

    public PlatformException(ErrorType type, String logMessage, Object... args) {
        this(type, null, logMessage, args);
    }
    
    public PlatformException(ErrorType type, Severity overrideSeverity, String logMessage, Object... args) {
        super(logMessage);
        this.type = type;
        this.args = args;
        this.overrideSeverity = overrideSeverity;
    }

    public Severity severity() {
        return overrideSeverity != null ? overrideSeverity : type.severity();
    }

    public static PlatformException of(ErrorType type, Object... args) {
        return new PlatformException(type, type.getId(), args);
    }
    public static PlatformException of(ErrorType type, Severity severity, Object... args) {
        return new PlatformException(type, severity, type.getId(), args);
    }
}
