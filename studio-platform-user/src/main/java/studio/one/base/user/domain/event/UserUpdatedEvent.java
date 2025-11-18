package studio.one.base.user.domain.event;

import java.io.Serializable;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Value;

@Value
public class UserUpdatedEvent implements Serializable , UserCacheEvictableEvent {

    UUID eventId; // 이벤트 고유 ID (멱등/추적용)
    Long userId; // 대상 사용자 ID
    String username;
    String actor; // 조치 수행자(시스템/관리자 등)
    OffsetDateTime occurredAt; // 이벤트 발생 시각(UTC 권장)

    public static UserUpdatedEvent of(Long userId, String username, String actor, Clock clock) {
        return new UserUpdatedEvent(UUID.randomUUID(),
                userId,
                username,
                actor,
                OffsetDateTime.now(clock));
    }

    public static UserUpdatedEvent now(Long userId, String username, String actor) {
        return of(userId, username, actor, Clock.systemUTC());
    }

}
