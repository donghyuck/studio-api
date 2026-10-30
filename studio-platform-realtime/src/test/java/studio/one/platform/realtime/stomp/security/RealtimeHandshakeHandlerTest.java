package studio.one.platform.realtime.stomp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.security.Principal;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeFailureException;

import studio.one.base.security.jwt.JwtTokenProvider;
import studio.one.platform.realtime.stomp.config.RealtimeStompProperties;

class RealtimeHandshakeHandlerTest {

    @Test
    void authenticatesAccessTokenFromQueryParameter() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        UserDetails details = User.withUsername("admin").password("n/a").roles("MAIL").build();
        when(tokenProvider.validateToken("query-token")).thenReturn(true);
        when(tokenProvider.getAuthentication(eq("query-token"), any(UserDetailsService.class), eq(true)))
                .thenReturn(new TestingAuthenticationToken(details, "", "ROLE_MAIL"));

        Principal principal = handler(tokenProvider).determineUser(
                request("http://localhost/ws?access_token=query-token", new HttpHeaders()),
                mock(WebSocketHandler.class),
                new HashMap<>());

        assertThat(principal.getName()).isEqualTo("admin");
        assertThat(((Authentication) principal).getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_MAIL");
    }

    @Test
    void authorizationHeaderTakesPrecedenceOverQueryParameter() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        UserDetails details = User.withUsername("header-user").password("n/a").roles("MAIL").build();
        when(tokenProvider.validateToken("header-token")).thenReturn(true);
        when(tokenProvider.getAuthentication(eq("header-token"), any(UserDetailsService.class), eq(true)))
                .thenReturn(new TestingAuthenticationToken(details, "", "ROLE_MAIL"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("header-token");

        Principal principal = handler(tokenProvider).determineUser(
                request("http://localhost/ws?access_token=query-token", headers),
                mock(WebSocketHandler.class),
                new HashMap<>());

        assertThat(principal.getName()).isEqualTo("header-user");
    }

    @Test
    void rejectsInactiveUserDetailsFromHandshakeToken() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.validateToken("disabled-token")).thenReturn(true);
        when(tokenProvider.getAuthentication(eq("disabled-token"), any(UserDetailsService.class), eq(true)))
                .thenThrow(new DisabledException("disabled"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("disabled-token");

        assertThatThrownBy(() -> handler(tokenProvider).determineUser(
                request("http://localhost/ws", headers),
                mock(WebSocketHandler.class),
                new HashMap<>()))
                .isInstanceOf(HandshakeFailureException.class);
    }

    @Test
    void allowsHandshakeWithoutTokenForStompConnectAuthentication() {
        Principal principal = handler(mock(JwtTokenProvider.class)).determineUser(
                request("http://localhost/ws", new HttpHeaders()),
                mock(WebSocketHandler.class),
                new HashMap<>());

        assertThat(principal).isNull();
    }

    private TestableRealtimeHandshakeHandler handler(JwtTokenProvider tokenProvider) {
        RealtimeStompProperties properties = new RealtimeStompProperties();
        properties.setJwtEnabled(true);
        properties.setRejectAnonymous(true);
        UserDetails details = User.withUsername("admin").password("n/a").roles("MAIL").build();
        return new TestableRealtimeHandshakeHandler(properties, tokenProvider, username -> details);
    }

    private ServerHttpRequest request(String uri, HttpHeaders headers) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(uri));
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }

    private static final class TestableRealtimeHandshakeHandler extends RealtimeHandshakeHandler {
        private TestableRealtimeHandshakeHandler(
                RealtimeStompProperties properties,
                JwtTokenProvider jwtTokenProvider,
                UserDetailsService userDetailsService) {
            super(properties, jwtTokenProvider, userDetailsService);
        }

        @Override
        public Principal determineUser(
                ServerHttpRequest request,
                WebSocketHandler wsHandler,
                java.util.Map<String, Object> attributes) {
            return super.determineUser(request, wsHandler, attributes);
        }
    }
}
