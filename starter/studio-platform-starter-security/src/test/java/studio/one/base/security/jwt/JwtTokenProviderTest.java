package studio.one.base.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import studio.one.base.security.domain.error.JwtTokenException;
import studio.one.platform.service.I18n;

class JwtTokenProviderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-23T00:00:00Z"), ZoneOffset.UTC);
    private static final SecretKey SECRET =
            Keys.hmacShaKeyFor("0123456789abcdef0123456789abcdef".getBytes());

    @Test
    void getAuthenticationPreservesActiveUserAuthorities() {
        JwtTokenProvider provider = provider(Duration.ofMinutes(5));
        UserDetails details = user("active", true, true, true, true);
        String token = tokenFor(details.getUsername(), Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));

        var authentication = provider.getAuthentication(token, username -> details, false);

        assertThat(authentication.getPrincipal()).isSameAs(details);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        assertThat(authentication.getCredentials()).isEqualTo("");
    }

    @Test
    void getAuthenticationRejectsDisabledUser() {
        assertBlocked(user("disabled", false, true, true, true), DisabledException.class);
    }

    @Test
    void getAuthenticationRejectsLockedUser() {
        assertBlocked(user("locked", true, false, true, true), LockedException.class);
    }

    @Test
    void getAuthenticationRejectsAccountExpiredUser() {
        assertBlocked(user("expired", true, true, false, true), AccountExpiredException.class);
    }

    @Test
    void getAuthenticationRejectsCredentialsExpiredUser() {
        assertBlocked(user("credentials", true, true, true, false), CredentialsExpiredException.class);
    }

    @Test
    void getAuthenticationRejectsDeletedUser() {
        JwtTokenProvider provider = provider(Duration.ofMinutes(5));
        String token = tokenFor("deleted", Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));

        assertThatThrownBy(() -> provider.getAuthentication(token, username -> {
            throw new UsernameNotFoundException(username);
        }, false))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void invalidJwtExceptionArgsDoNotContainRawToken() {
        JwtTokenProvider provider = provider(Duration.ofMinutes(5));
        String raw = "not-a-jwt";

        assertThatThrownBy(() -> provider.validateToken(raw))
                .isInstanceOf(JwtTokenException.class)
                .satisfies(ex -> assertThat(((JwtTokenException) ex).getArgs()).doesNotContain(raw));
    }

    @Test
    void expiredJwtExceptionArgsDoNotContainRawToken() {
        JwtTokenProvider provider = provider(Duration.ofMinutes(5));
        String raw = tokenFor("expired-token", Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> provider.validateToken(raw))
                .isInstanceOf(JwtTokenException.class)
                .satisfies(ex -> assertThat(((JwtTokenException) ex).getArgs()).doesNotContain(raw));
    }

    private void assertBlocked(UserDetails loadedDetails, Class<? extends RuntimeException> type) {
        JwtTokenProvider provider = provider(Duration.ofMinutes(5));
        String token = tokenFor(loadedDetails.getUsername(), Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));

        assertThatThrownBy(() -> provider.getAuthentication(token, username -> loadedDetails, false))
                .isInstanceOf(type);
    }

    private String tokenFor(String username, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject(username)
                .claim("authorities", "ROLE_USER")
                .issuer("studio-test")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(SECRET, Jwts.SIG.HS256)
                .compact();
    }

    private JwtTokenProvider provider(Duration accessTtl) {
        return new JwtTokenProvider(
                SECRET,
                "studio-test",
                accessTtl,
                Duration.ofDays(7),
                JwtTokenProvider.HEADER_STRING,
                "authorities",
                "refresh_token",
                "/api/auth",
                false,
                "Strict",
                CLOCK,
                i18n());
    }

    private UserDetails user(String username, boolean enabled, boolean accountNonLocked,
            boolean accountNonExpired, boolean credentialsNonExpired) {
        return User.withUsername(username)
                .password("n/a")
                .roles("USER")
                .disabled(!enabled)
                .accountLocked(!accountNonLocked)
                .accountExpired(!accountNonExpired)
                .credentialsExpired(!credentialsNonExpired)
                .build();
    }

    private I18n i18n() {
        return (code, args, locale) -> code;
    }
}
