package studio.one.base.security.audit;

import java.time.Instant;

public record LoginFailureAuditEventData(
    String username,
    String remoteIp,
    String userAgent,
    String failureType,
    String message,
    Instant occurredAt) {
}
