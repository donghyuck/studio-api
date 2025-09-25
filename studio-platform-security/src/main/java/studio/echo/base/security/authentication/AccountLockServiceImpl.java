package studio.echo.base.security.authentication;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.security.audit.LoginFailureLogRepository;
import studio.echo.base.user.domain.entity.ApplicationUser;
import studio.echo.base.user.domain.repository.ApplicationUserRepository;

@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccountLockServiceImpl implements AccountLockService {

    private final ApplicationUserRepository userRepo;

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

        userRepo.findByUsernameForUpdate(username).ifPresent(u -> {

            int attempts;
            boolean withinWindow = false;
            if (useWindow() && u.getLastFailedAt() != null) {
                withinWindow = Duration.between(u.getLastFailedAt(), now).compareTo(window) <= 0;
            }
            attempts = withinWindow ? (u.getFailedAttempts() + 1) : 1;
            u.setFailedAttempts(attempts);
            u.setLastFailedAt(now);
            log.debug("[LOCK] user='{}' withinWindow={}, attempts={}", username, withinWindow, attempts);
            if (lockingEnabled && attempts >= maxAttempts) {
                // 잠금 기간이 유효하면 잠금 until 설정/연장
                Instant until = now.plus(lockDuration);
                if (u.getAccountLockedUntil() == null || u.getAccountLockedUntil().isBefore(until)) {
                    u.setAccountLockedUntil(until);
                }
                log.info("[LOCK] user='{}' locked until {}", username, u.getAccountLockedUntil());
            }
        });
    }

    @Override
    public void onSuccessfulLogin(String username) {
        if (!resetOnSuccess || StringUtils.isBlank(username))
            return;

        final Instant now = Instant.now(clock);
        userRepo.findByUsername(username).ifPresent(u -> {
            u.setFailedAttempts(0);
            u.setLastFailedAt(null);
            u.setAccountLockedUntil(null);

            if (u.getAccountLockedUntil() != null && u.getAccountLockedUntil().isAfter(now)) {
                u.setAccountLockedUntil(null);
            }
            log.debug("[LOCK] user='{}' counters reset on success", username);

        });
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<Instant> getLockedUntil(String username) {
        if (username == null || username.isBlank())
            return Optional.empty();
        return userRepo.findByUsername(username).map(ApplicationUser::getAccountLockedUntil);
    }

}
