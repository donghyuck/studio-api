package studio.echo.base.user.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@com.fasterxml.jackson.annotation.JsonInclude(JsonInclude.Include.NON_NULL)
public class MeProfileDto {
    Long userId;
    String username;
    String name;
    String email;
    Boolean enabled;
    java.util.List<String> roles;
    java.time.OffsetDateTime createdAt;
    java.time.OffsetDateTime updatedAt;
}
