package studio.one.platform.realtime.stomp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.security.Principal;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeFailureException;

import studio.one.base.security.jwt.JwtTokenProvider;
import studio.one.platform.realtime.stomp.config.RealtimeStompProperties;

class RealtimeHandshakeHandlerTest {

    @Test
    void authenticatesAccessTokenFromQueryParameter() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.validateToken("query-token")).thenReturn(true);
        when(tokenProvider.getUsername("query-token")).thenReturn("admin");

        Principal principal = handler(tokenProvider).determineUser(
                request("http://localhost/ws?access_token=query-token", new HttpHeaders()),
                mock(WebSocketHandler.class),
                new HashMap<>());

        assertThat(principal.getName()).isEqualTo("admin");
    }

    @Test
    void authorizationHeaderTakesPrecedenceOverQueryParameter() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.validateToken("header-token")).thenReturn(true);
        when(tokenProvider.getUsername("header-token")).thenReturn("header-user");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("header-token");

        Principal principal = handler(tokenProvider).determineUser(
                request("http://localhost/ws?access_token=query-token", headers),
                mock(WebSocketHandler.class),
                new HashMap<>());

        assertThat(principal.getName()).isEqualTo("header-user");
    }

    @Test
    void rejectsConnectionWithoutToken() {
        assertThatThrownBy(() -> handler(mock(JwtTokenProvider.class)).determineUser(
                request("http://localhost/ws", new HttpHeaders()),
                mock(WebSocketHandler.class),
                new HashMap<>()))
                .isInstanceOf(HandshakeFailureException.class)
                .hasMessage("Valid JWT bearer token is required");
    }

    private TestableRealtimeHandshakeHandler handler(JwtTokenProvider tokenProvider) {
        RealtimeStompProperties properties = new RealtimeStompProperties();
        properties.setJwtEnabled(true);
        properties.setRejectAnonymous(true);
        return new TestableRealtimeHandshakeHandler(properties, tokenProvider);
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
                JwtTokenProvider jwtTokenProvider) {
            super(properties, jwtTokenProvider);
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
