package studio.one.base.user.domain.event;

import java.io.Serializable;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Value;

@Value
public class UserDisabledEvent implements Serializable{

    UUID eventId;
    Long userId;
    String actor;
    String reason;
    OffsetDateTime until; // nullable
    OffsetDateTime occurredAt;

    public static UserDisabledEvent of(Long userId, String actor, String reason,
            OffsetDateTime until, Clock clock) {
        return new UserDisabledEvent(
                UUID.randomUUID(), userId, actor, reason, until, OffsetDateTime.now(clock));
    }
}
