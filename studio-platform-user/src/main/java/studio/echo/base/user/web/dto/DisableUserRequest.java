package studio.echo.base.user.web.dto;

import java.time.OffsetDateTime;

import javax.validation.constraints.FutureOrPresent;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DisableUserRequest {
    @Size(max = 500)
    String reason;

    /** null이면 영구 비활성화. 존재하면 현재 시각 이후여야 함. */
    @FutureOrPresent(message = "until must be now or in the future")
    @JsonFormat(shape = JsonFormat.Shape.STRING) // ISO-8601 (예: 2025-09-01T06:00:00Z)
    OffsetDateTime until;

    /** 액세스/리프레시 토큰 무효화 여부 (기본 true) */
    @Builder.Default
    boolean revokeTokens = true;

    /** 서버 세션 무효화 여부 (기본 true) */
    @Builder.Default
    boolean invalidateSessions = true;

    /** 사용자에게 알림 전송 여부 (기본 false) */
    @Builder.Default
    boolean notifyUser = false;
}
