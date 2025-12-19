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
 * - jwtEnabled=false 또는 JwtTokenProvider 미존재 시 기본 Principal/익명 허용
 * - rejectAnonymous=true 이면서 사용자 정보가 없으면 연결 거부
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
        if (!properties.isJwtEnabled() || jwtTokenProvider == null) {
            return allowOrAnonymous(null, request, wsHandler, attributes);
        }
        try {
            String token = resolveToken(request.getHeaders());
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                return allowOrAnonymous(null, request, wsHandler, attributes);
            }
            String name = jwtTokenProvider.getUsername(token);
            return allowOrAnonymous(name, request, wsHandler, attributes);
        } catch (Exception ex) {
            log.debug("Handshake JWT decode failed: {}", ex.getMessage());
            return allowOrAnonymous(null, request, wsHandler, attributes);
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
