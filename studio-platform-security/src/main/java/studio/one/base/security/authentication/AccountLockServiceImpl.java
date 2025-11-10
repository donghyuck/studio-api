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
 *      @file AccountLockServiceImpl.java
 *      @date 2025
 *
 */
package studio.one.base.security.authentication;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.audit.domain.repository.LoginFailureLogRepository;
import studio.one.base.user.persistence.AccountLockRepository;

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


@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccountLockServiceImpl implements AccountLockService {

    private final AccountLockRepository accountLockRepository;

    private final LoginFailureLogRepository failureLogRepo;

    private final Clock clock;

    private final int maxAttempts;

    private final Duration window;

    private final Duration lockDuration;

    private final boolean resetOnSuccess;

    private boolean isEnabled() {
        return maxAttempts > 0
                && lockDuration != null
                && !lockDuration.isZero()
                && !lockDuration.isNegative();
    }

    private boolean useWindow() {
        return window != null && !window.isZero() && !window.isNegative();
    }

    @Override
    public void onFailedLogin(String username) {
        if (StringUtils.isBlank(username))
            return;

        final boolean lockingEnabled = isEnabled();
        final Instant now = Instant.now(clock);

        log.debug("[LOCK] failed login for '{}': enabled={}, maxAttempts={}, window={}, lockDuration={}",
                username, lockingEnabled, maxAttempts, window, lockDuration);

        boolean withinWindow = false;
        if (useWindow()) {
            Instant lastFailedAt = accountLockRepository.findLastFailedAt(username);
            if (lastFailedAt != null) {
                withinWindow = Duration.between(lastFailedAt, now).compareTo(window) <= 0;
            }
        }

        if (!withinWindow) {
            accountLockRepository.resetLockState(username);
        }

        int updated = accountLockRepository.bumpFailedAttempts(username, now);
        if (updated == 0) {
            log.debug("[LOCK] user='{}' does not exist, skipping", username);
            return;
        }

        int attempts = Optional.ofNullable(accountLockRepository.findFailedAttempts(username)).orElse(0);
        log.debug("[LOCK] user='{}' withinWindow={}, attempts={}", username, withinWindow, attempts);

        if (lockingEnabled && attempts >= maxAttempts) {
            Instant until = now.plus(lockDuration);
            Instant current = accountLockRepository.findAccountLockedUntil(username);
            if (current == null || current.isBefore(until)) {
                accountLockRepository.lockUntil(username, until);
                log.info("[LOCK] user='{}' locked until {}", username, until);
            }
        }
    }

    @Override
    public void onSuccessfulLogin(String username) {
        if (!resetOnSuccess || StringUtils.isBlank(username))
            return;

        int updated = accountLockRepository.resetLockState(username);
        if (updated > 0) {
            log.debug("[LOCK] user='{}' counters reset on success", username);
        }
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<Instant> getLockedUntil(String username) {
        if (username == null || username.isBlank())
            return Optional.empty();
        return Optional.ofNullable(accountLockRepository.findAccountLockedUntil(username));
    }

}
