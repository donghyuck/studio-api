package studio.echo.base.security.web.controller;

import java.time.Duration;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.security.jwt.JwtTokenProvider;
import studio.echo.base.user.web.dto.LoginRequest;
import studio.echo.base.user.web.dto.LoginResponse;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.service.I18n;
import studio.echo.platform.web.annotation.Message;
import studio.echo.platform.web.dto.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("${" + PropertyKeys.Security.Jwt.Endpoints.BASE_PATH + ":/api/auth}")
@Slf4j
public class JwtAuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final I18n i18n;

    @PostMapping("/login")
    @Message("success.security.auth.login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        String accessToken = jwtTokenProvider.generateToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);
        String cookieName = "refresh_token";
        Integer maxAgeSec = jwtTokenProvider.getMaxAgeForRefreshTtl(); // -1 면 세션 쿠키

        ResponseCookie.ResponseCookieBuilder rb = ResponseCookie.from(cookieName, refreshToken)
                .httpOnly(Boolean.TRUE)
                .secure(Boolean.TRUE) // 
                .path("/")  
                .sameSite("None"); // 크로스사이트 허용

        if (maxAgeSec != null && maxAgeSec >= 0) {
            rb.maxAge(Duration.ofSeconds(maxAgeSec));
        } // 세션 쿠키는 maxAge 미설정

        ResponseCookie rc = rb.build();
        response.addHeader("Set-Cookie", rc.toString());

        // 4) 로그 (i18n 인자 전달)
        log.info(i18n.get(
                "info.security.jwt.cookie.set",
                cookieName, Boolean.TRUE, Boolean.TRUE, (maxAgeSec == null ? "-" : maxAgeSec)));
        return ResponseEntity.ok(ApiResponse.ok(new LoginResponse(accessToken)));
    }

}
