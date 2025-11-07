package studio.one.platform.storage.web.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class PresignedUrlDto {
    private final String url;
    private final Instant expiresAt;
}