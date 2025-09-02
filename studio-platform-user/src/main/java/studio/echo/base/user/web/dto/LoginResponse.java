package studio.echo.base.user.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class LoginResponse {
    private String accessToken;
}
