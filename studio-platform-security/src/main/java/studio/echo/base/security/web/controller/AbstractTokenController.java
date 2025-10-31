package studio.echo.base.security.web.controller;

import java.time.Duration;

import javax.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseCookie;

import lombok.extern.slf4j.Slf4j;
import studio.echo.base.security.jwt.JwtTokenProvider;
import studio.echo.platform.service.I18n;

@Slf4j
public abstract class AbstractTokenController {

    protected void addRefreshCookie(I18n i18n, JwtTokenProvider jwtTokenProvider, HttpServletResponse response,
            String raw) {
        String cookieName = "refresh_token";
        Integer maxAgeSec = jwtTokenProvider.getMaxAgeForRefreshTtl(); // -1 면 세션 쿠키
        ResponseCookie.ResponseCookieBuilder rb = ResponseCookie.from(cookieName, raw)
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

    }

}
