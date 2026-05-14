package studio.one.base.security.web.dto.response;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public class LoginFailureLogDto {

    private final Long id;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final OffsetDateTime occurredAt;
    private final String username;
    private final String remoteIp;
    private final String failureType;
    private final String message;
    private final String userAgent;

    public LoginFailureLogDto(Long id, OffsetDateTime occurredAt, String username, String remoteIp, String failureType, String message, String userAgent) {
        this.id = id; this.occurredAt = occurredAt; this.username = username; this.remoteIp = remoteIp; this.failureType = failureType; this.message = message; this.userAgent = userAgent;
    }

    public Long id() { return id; }
    public OffsetDateTime occurredAt() { return occurredAt; }
    public String username() { return username; }
    public String remoteIp() { return remoteIp; }
    public String failureType() { return failureType; }
    public String message() { return message; }
    public String userAgent() { return userAgent; }

    public Long getId() { return id; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public String getUsername() { return username; }
    public String getRemoteIp() { return remoteIp; }
    public String getFailureType() { return failureType; }
    public String getMessage() { return message; }
    public String getUserAgent() { return userAgent; }
}
