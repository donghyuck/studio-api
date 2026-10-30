package studio.one.platform.realtime.stomp.security;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeFailureException;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.realtime.stomp.config.RealtimeStompProperties;

/**
 * WebSocket Handshake 시 JWT를 통한 Principal 주입을 처리한다.
 * - jwtEnabled=true 이고 handshake에 JWT가 있으면 즉시 Principal을 생성한다.
 * - JWT가 없으면 STOMP CONNECT 단계의 인증을 위해 handshake를 허용한다.
 * - jwtEnabled=false 일 때만 익명 Principal 허용 정책(rejectAnonymous)에 따른다.
 */
@RequiredArgsConstructor
@Slf4j
public class RealtimeHandshakeHandler extends DefaultHandshakeHandler {

    private final RealtimeStompProperties properties;
    private final Object jwtTokenProvider;
    private final Object userDetailsService;

    public RealtimeHandshakeHandler(
            RealtimeStompProperties properties,
            Object jwtTokenProvider) {
        this(properties, jwtTokenProvider, null);
    }

    @Override
    protected Principal determineUser(org.springframework.http.server.ServerHttpRequest request,
            WebSocketHandler wsHandler, java.util.Map<String, Object> attributes) {
        // 이미 인증된 Principal 이 있으면 사용
        Principal existing = request.getPrincipal();
        if (existing != null) {
            return existing;
        }
        if (!properties.isJwtEnabled()) {
            return allowOrAnonymous(null, request, wsHandler, attributes);
        }
        if (jwtTokenProvider == null) {
            throw new HandshakeFailureException("JWT-enabled WebSocket connections require a JwtTokenProvider");
        }
        try {
            String token = resolveToken(request);
            if (token == null) {
                return super.determineUser(request, wsHandler, attributes);
            }
            if (!validateToken(token)) {
                throw new HandshakeFailureException("Valid JWT bearer token is required");
            }
            Principal principal = authenticate(token);
            if (principal == null) {
                throw new HandshakeFailureException("JWT handshake failed");
            }
            return principal;
        } catch (Exception ex) {
            log.debug("Handshake JWT decode failed: {}", ex.getMessage());
            if (ex instanceof HandshakeFailureException hfe) {
                throw hfe;
            }
            throw new HandshakeFailureException("JWT handshake failed", ex);
        }
    }

    @Nullable
    private String resolveToken(org.springframework.http.server.ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        List<String> auth = headers.get(HttpHeaders.AUTHORIZATION);
        if (auth != null && !auth.isEmpty()) {
            String bearer = auth.get(0);
            if (bearer.regionMatches(true, 0, "bearer ", 0, 7)) {
                return bearer.substring(7);
            }
        }
        return UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("access_token");
    }

    private record SimplePrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }

    private Principal authenticate(String token) {
        if (userDetailsService == null) {
            throw new HandshakeFailureException("JWT-enabled WebSocket connections require a UserDetailsService");
        }
        try {
            Class<?> userDetailsServiceType =
                    Class.forName("org.springframework.security.core.userdetails.UserDetailsService");
            Object authentication = jwtTokenProvider.getClass()
                    .getMethod("getAuthentication", String.class, userDetailsServiceType, boolean.class)
                    .invoke(jwtTokenProvider, token, userDetailsService, true);
            return authentication instanceof Principal principal ? principal : null;
        } catch (ReflectiveOperationException | LinkageError ex) {
            throw new HandshakeFailureException("JWT handshake failed", ex);
        }
    }

    private boolean validateToken(String token) {
        try {
            Object result = jwtTokenProvider.getClass()
                    .getMethod("validateToken", String.class)
                    .invoke(jwtTokenProvider, token);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    private Principal allowOrAnonymous(String name,
            org.springframework.http.server.ServerHttpRequest request,
            WebSocketHandler wsHandler, java.util.Map<String, Object> attributes) {
        if (name == null && properties.isRejectAnonymous()) {
            throw new HandshakeFailureException("Anonymous WebSocket connections are not allowed");
        }
        if (name != null) {
            return new SimplePrincipal(name);
        }
        return super.determineUser(request, wsHandler, attributes);
    }
}
