package studio.one.base.security.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import studio.one.base.security.jwt.JwtTokenProvider;
import studio.one.base.security.jwt.refresh.RefreshTokenStore;
import studio.one.base.security.userdetails.UserIdToUsername;

class JwtRefreshControllerTest {

    @Test
    void refreshPreservesActiveUserTokenAndCookieRotation() throws Exception {
        JwtTokenProvider tokenProvider = tokenProvider();
        RefreshTokenStore tokenStore = mock(RefreshTokenStore.class);
        UserDirectory userDirectory = new UserDirectory()
                .add(7L, user("active", true, true, true, true));
        JwtRefreshController controller = controller(tokenProvider, tokenStore, userDirectory);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenStore.resolveUserId("raw-refresh")).thenReturn(7L);
        when(tokenProvider.generateToken(any(Authentication.class))).thenReturn("access-token");
        when(tokenStore.rotate("raw-refresh")).thenReturn("rotated-refresh");

        var entity = controller.refresh(request("raw-refresh"), response);

        assertThat(entity.getBody().getData().getAccessToken()).isEqualTo("access-token");
        assertThat(response.getHeader("Set-Cookie")).contains("refresh_token=rotated-refresh");
        verify(tokenStore).rotate("raw-refresh");
    }

    @Test
    void refreshRejectsDisabledUserBeforeIssuingTokens() throws Exception {
        assertRefreshBlocked(user("disabled", false, true, true, true), DisabledException.class);
    }

    @Test
    void refreshRejectsLockedUserBeforeIssuingTokens() throws Exception {
        assertRefreshBlocked(user("locked", true, false, true, true), LockedException.class);
    }

    @Test
    void refreshRejectsAccountExpiredUserBeforeIssuingTokens() throws Exception {
        assertRefreshBlocked(user("expired", true, true, false, true), AccountExpiredException.class);
    }

    @Test
    void refreshRejectsCredentialsExpiredUserBeforeIssuingTokens() throws Exception {
        assertRefreshBlocked(user("credentials", true, true, true, false), CredentialsExpiredException.class);
    }

    @Test
    void refreshRejectsDeletedUserBeforeIssuingTokens() throws Exception {
        JwtTokenProvider tokenProvider = tokenProvider();
        RefreshTokenStore tokenStore = mock(RefreshTokenStore.class);
        UserDirectory userDirectory = new UserDirectory();
        JwtRefreshController controller = controller(tokenProvider, tokenStore, userDirectory);

        when(tokenStore.resolveUserId("raw-refresh")).thenReturn(404L);

        assertThatThrownBy(() -> controller.refresh(request("raw-refresh"), new MockHttpServletResponse()))
                .isInstanceOf(UsernameNotFoundException.class);
        verify(tokenProvider, never()).generateToken(any(Authentication.class));
        verify(tokenStore, never()).rotate("raw-refresh");
    }

    private void assertRefreshBlocked(UserDetails details, Class<? extends RuntimeException> type) throws Exception {
        JwtTokenProvider tokenProvider = tokenProvider();
        RefreshTokenStore tokenStore = mock(RefreshTokenStore.class);
        UserDirectory userDirectory = new UserDirectory().add(7L, details);
        JwtRefreshController controller = controller(tokenProvider, tokenStore, userDirectory);

        when(tokenStore.resolveUserId("raw-refresh")).thenReturn(7L);

        assertThatThrownBy(() -> controller.refresh(request("raw-refresh"), new MockHttpServletResponse()))
                .isInstanceOf(type);
        verify(tokenProvider, never()).generateToken(any(Authentication.class));
        verify(tokenStore, never()).rotate("raw-refresh");
    }

    private JwtRefreshController controller(JwtTokenProvider tokenProvider,
            RefreshTokenStore tokenStore,
            UserDetailsService userDetailsService) throws Exception {
        JwtRefreshController controller = new JwtRefreshController(tokenProvider, tokenStore, userDetailsService,
                (code, args, locale) -> code);
        Field field = JwtRefreshController.class.getDeclaredField("refreshCookieName");
        field.setAccessible(true);
        field.set(controller, "refresh_token");
        return controller;
    }

    private JwtTokenProvider tokenProvider() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.getRefreshCookieName()).thenReturn("refresh_token");
        when(tokenProvider.getMaxAgeForRefreshTtl()).thenReturn(3600);
        when(tokenProvider.isCookieSecure()).thenReturn(false);
        when(tokenProvider.getCookiePath()).thenReturn("/api/auth");
        when(tokenProvider.getCookieSameSite()).thenReturn("Strict");
        return tokenProvider;
    }

    private MockHttpServletRequest request(String raw) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.setCookies(new MockCookie("refresh_token", raw));
        return request;
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

    private static final class UserDirectory implements UserDetailsService, UserIdToUsername {
        private final Map<Long, UserDetails> byId = new HashMap<>();
        private final Map<String, UserDetails> byUsername = new HashMap<>();

        private UserDirectory add(Long userId, UserDetails details) {
            byId.put(userId, details);
            byUsername.put(details.getUsername(), details);
            return this;
        }

        @Override
        public String usernameOf(long userId) {
            UserDetails details = byId.get(userId);
            if (details == null) {
                throw new UsernameNotFoundException("deleted");
            }
            return details.getUsername();
        }

        @Override
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
            UserDetails details = byUsername.get(username);
            if (details == null) {
                throw new UsernameNotFoundException(username);
            }
            return details;
        }
    }
}
