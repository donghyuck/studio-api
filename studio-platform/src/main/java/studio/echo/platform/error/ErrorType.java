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
 *      @file ErrorType.java
 *      @date 2025
 *
 */


package studio.echo.platform.error;

import java.util.Objects;

import org.springframework.http.HttpStatus;
/**
 * 에러 타입 인터페이스
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


public interface ErrorType {

    String getId(); // 예: "user.not-found"

    HttpStatus getStatus(); // 응답 HTTP 상태

    Severity getSeverity(); 
    
    default Severity severity() { // 기본 심각도 (필수 아님, 기본 ERROR)
        return Severity.ERROR;
    }

    static ErrorType of(String id, HttpStatus status) {
        return of(id, status, Severity.ERROR);
    }
    
    static ErrorType of(String id, HttpStatus status, Severity severity) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Severity sev = (severity != null ? severity : Severity.ERROR);

        // 간단: 익명 클래스 반환 (불변)
        return new ErrorType() {
            @Override public String getId() { return id; }
            @Override public HttpStatus getStatus() { return status; }
            @Override public Severity getSeverity() { return sev; }
            @Override public String toString() { return id + " [" + status + ", " + sev + "]"; }
        };
    }
}
