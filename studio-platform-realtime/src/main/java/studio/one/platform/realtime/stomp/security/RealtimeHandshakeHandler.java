package studio.one.platform.realtime.stomp.security;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeFailureException;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.realtime.stomp.config.RealtimeStompProperties;
import studio.one.base.security.jwt.JwtTokenProvider;

/**
 * WebSocket Handshake 시 JWT를 통한 Principal 주입을 처리한다.
 * - jwtEnabled=true 이면 유효한 JWT가 필요하다.
 * - jwtEnabled=false 일 때만 익명 Principal 허용 정책(rejectAnonymous)에 따른다.
 */
@RequiredArgsConstructor
@Slf4j
public class RealtimeHandshakeHandler extends DefaultHandshakeHandler {

    private final RealtimeStompProperties properties;
    private final JwtTokenProvider jwtTokenProvider;

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
            String token = resolveToken(request.getHeaders());
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                throw new HandshakeFailureException("Valid JWT bearer token is required");
            }
            String name = jwtTokenProvider.getUsername(token);
            return new SimplePrincipal(name);
        } catch (Exception ex) {
            log.debug("Handshake JWT decode failed: {}", ex.getMessage());
            if (ex instanceof HandshakeFailureException hfe) {
                throw hfe;
            }
            throw new HandshakeFailureException("JWT handshake failed", ex);
        }
    }

    @Nullable
    private String resolveToken(HttpHeaders headers) {
        List<String> auth = headers.get(HttpHeaders.AUTHORIZATION);
        if (auth == null || auth.isEmpty()) {
            return null;
        }
        String bearer = auth.get(0);
        if (bearer.toLowerCase().startsWith("bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    private record SimplePrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
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
