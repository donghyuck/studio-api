package studio.echo.base.user.domain.event;

import java.io.Serializable;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Value;

@Value
public class UserEnabledEvent implements Serializable {
    UUID eventId; // 이벤트 고유 ID (멱등/추적용)
    Long userId; // 대상 사용자 ID
    String actor; // 조치 수행자(시스템/관리자 등)
    OffsetDateTime occurredAt; // 이벤트 발생 시각(UTC 권장)

    public static UserEnabledEvent of(Long userId, String actor, Clock clock) {
        return new UserEnabledEvent(
                UUID.randomUUID(),
                userId,
                actor,
                OffsetDateTime.now(clock));
    }

    public static UserEnabledEvent now(Long userId, String actor) {
        return of(userId, actor, Clock.systemUTC());
    }
}
