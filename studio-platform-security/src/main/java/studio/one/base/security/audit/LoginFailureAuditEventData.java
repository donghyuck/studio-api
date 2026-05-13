package studio.one.base.security.audit;

import java.time.Instant;

public class LoginFailureAuditEventData {

    private final String username;
    private final String remoteIp;
    private final String userAgent;
    private final String failureType;
    private final String message;
    private final Instant occurredAt;

    public LoginFailureAuditEventData(String username, String remoteIp, String userAgent, String failureType, String message, Instant occurredAt) {
        this.username = username; this.remoteIp = remoteIp; this.userAgent = userAgent; this.failureType = failureType; this.message = message; this.occurredAt = occurredAt;
    }

    public String username() { return username; }
    public String remoteIp() { return remoteIp; }
    public String userAgent() { return userAgent; }
    public String failureType() { return failureType; }
    public String message() { return message; }
    public Instant occurredAt() { return occurredAt; }
}
