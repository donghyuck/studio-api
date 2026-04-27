package studio.one.base.security.web.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import studio.one.base.security.jwt.JwtConfig;
import studio.one.base.security.jwt.JwtTokenProvider;
import studio.one.platform.service.I18n;

class JwtCookieSettingsTest {

    @Test
    void providerPreservesConfiguredCookieSettings() {
        JwtTokenProvider provider = provider();

        assertEquals("custom_refresh", provider.getRefreshCookieName());
        assertEquals("/custom/auth", provider.getCookiePath());
        assertFalse(provider.isCookieSecure());
        assertEquals("Lax", provider.getCookieSameSite());
    }

    @Test
    void addRefreshCookieUsesConfiguredCookieSettings() {
        JwtTokenProvider provider = provider();
        HttpServletResponse response = mock(HttpServletResponse.class);
        I18n i18n = (code, args, locale) -> code;

        new TestController().issue(provider, response, i18n);

        var headerValue = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("Set-Cookie"), headerValue.capture());
        String cookie = headerValue.getValue();

        assertTrue(cookie.contains("custom_refresh=token"));
        assertTrue(cookie.contains("Path=/custom/auth"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Max-Age=300"));
        assertFalse(cookie.contains("Secure"));
    }

    private JwtTokenProvider provider() {
        return new JwtTokenProvider(
                new TestJwtConfig(),
                Clock.fixed(Instant.parse("2026-03-23T00:00:00Z"), ZoneOffset.UTC),
                (code, args, locale) -> code);
    }

    private static final class TestController extends AbstractTokenController {
        void issue(JwtTokenProvider provider, HttpServletResponse response, I18n i18n) {
            addRefreshCookie(i18n, provider, response, "token");
        }
    }

    private static final class TestJwtConfig implements JwtConfig {
        @Override
        public Duration getAccessTtl() {
            return Duration.ofMinutes(15);
        }

        @Override
        public Duration getRefreshTtl() {
            return Duration.ofMinutes(5);
        }

        @Override
        public Duration getRotationGrace() {
            return Duration.ZERO;
        }

        @Override
        public String getHeader() {
            return HttpHeaders.AUTHORIZATION;
        }

        @Override
        public String getPrefix() {
            return "Bearer";
        }

        @Override
        public String getClaimAuthorities() {
            return "authorities";
        }

        @Override
        public String getRefreshCookieName() {
            return "custom_refresh";
        }

        @Override
        public String getCookiePath() {
            return "/custom/auth";
        }

        @Override
        public boolean isCookieSecure() {
            return false;
        }

        @Override
        public String getCookieSameSite() {
            return "Lax";
        }

        @Override
        public String getSecret() {
            return "01234567890123456789012345678901";
        }

        @Override
        public String getIssuer() {
            return "studio-test";
        }
    }
}
