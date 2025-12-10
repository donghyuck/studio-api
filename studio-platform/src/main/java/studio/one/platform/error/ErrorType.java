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


package studio.one.platform.error;

import java.util.Objects;

import org.springframework.http.HttpStatus;
/**
 * An interface that represents a type of error in the system.
 * 
 * @author  donghyuck, son
 * @since 2025-08-12
 * @version 1.0
 */
public interface ErrorType {

    /**
     * Returns the unique identifier of the error type (e.g., "user.not-found").
     *
     * @return the error ID
     */
    String getId();

    /**
     * Returns the HTTP status associated with the error.
     *
     * @return the HTTP status
     */
    HttpStatus getStatus();

    /**
     * Returns the severity of the error.
     *
     * @return the error severity
     */
    Severity getSeverity(); 
    
    /**
     * Returns the severity of the error, with a default of {@link Severity#ERROR}.
     *
     * @return the error severity
     */
    default Severity severity() {
        return Severity.ERROR;
    }

    static ErrorType of(String id) {
        return of(id, HttpStatus.INTERNAL_SERVER_ERROR, Severity.ERROR);
    }

    /**
     * Creates a new {@code ErrorType} with the specified ID and status, and a
     * default severity of {@link Severity#ERROR}.
     *
     * @param id     the error ID
     * @param status the HTTP status
     * @return a new {@code ErrorType} instance
     */
    static ErrorType of(String id, HttpStatus status) {
        return of(id, status, Severity.ERROR);
    }
    
    /**
     * Creates a new {@code ErrorType} with the specified ID, status, and
     * severity.
     *
     * @param id       the error ID
     * @param status   the HTTP status
     * @param severity the error severity
     * @return a new {@code ErrorType} instance
     */
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
