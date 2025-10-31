package studio.echo.base.security.web.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public record LoginFailureLogDto(
        Long id,
        
        @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime occurredAt,

        String username,
        String remoteIp,
        String failureType,
        String message,
        String userAgent
) {}