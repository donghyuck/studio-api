package studio.one.platform.ai.core.chat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/** Trusted execution time, separate from retrieved document facts. */
public record ChatRuntimeContext(Instant asOf, ZoneId zone) {
    public ChatRuntimeContext {
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(zone, "zone");
    }
    public static ChatRuntimeContext capture(Clock clock, ZoneId zone) {
        return new ChatRuntimeContext(clock.instant(), zone);
    }
    public ZonedDateTime localTime() { return asOf.atZone(zone); }
}
