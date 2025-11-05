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
 *      @file AccountLockService.java
 *      @date 2025
 *
 */


package studio.one.base.security.authentication;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 *
 * @author  donghyuck, son
 * @since 2025-09-29
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-09-29  donghyuck, son: 최초 생성.
 * </pre>
 */


public interface AccountLockService {

    void onFailedLogin(String username);

    void onSuccessfulLogin(String username);

    Optional<Instant> getLockedUntil(String username);

    default long getRemainingLockSeconds(String username, Clock clock) {
        return getLockedUntil(username)
                .map(until -> Math.max(0, Duration.between(Instant.now(clock), until).getSeconds()))
                .orElse(0L);
    }
}
