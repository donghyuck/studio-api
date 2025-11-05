package studio.one.platform.security.autoconfigure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.constant.PropertyKeys;

@Getter
@Setter
@ConfigurationProperties(prefix = PropertyKeys.Security.Auth.LOCK)
public class AccountLockProperties {
    /** 최대 실패 허용 횟수 (초과 시 잠금) */
    private int maxAttempts = 5;

    /** 실패 횟수 집계 기간(옵션). 0이면 무제한 누적 */
    private Duration window = Duration.ZERO;

    /** 자동 잠금 해제까지 유지할 시간(옵션). 0이면 자동 해제 안 함 */
    private Duration lockDuration = Duration.ZERO;

    /** 성공 시 실패횟수/잠금 리셋 여부 */
    private boolean resetOnSuccess = true;
}
