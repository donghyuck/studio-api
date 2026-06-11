package studio.one.platform.realtime.stomp.security;

import java.security.Principal;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.server.HandshakeFailureException;

import lombok.RequiredArgsConstructor;
import studio.one.base.security.jwt.JwtTokenProvider;
import studio.one.platform.realtime.stomp.config.RealtimeStompProperties;

@RequiredArgsConstructor
public class RealtimeStompAuthChannelInterceptor implements ChannelInterceptor {

    private final RealtimeStompProperties properties;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !properties.isJwtEnabled()) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            StompHeaderAccessor mutableAccessor = StompHeaderAccessor.wrap(message);
            mutableAccessor.setUser(authenticate(mutableAccessor.getFirstNativeHeader("Authorization")));
            return MessageBuilder.createMessage(message.getPayload(), mutableAccessor.getMessageHeaders());
        }
        if (requiresAuthentication(accessor.getCommand()) && accessor.getUser() == null) {
            throw new HandshakeFailureException("Authenticated STOMP connection is required");
        }
        return message;
    }

    private Principal authenticate(String authorization) {
        String token = bearerToken(authorization);
        if (token == null || jwtTokenProvider == null || !jwtTokenProvider.validateToken(token)) {
            throw new HandshakeFailureException("Valid JWT bearer token is required in STOMP CONNECT");
        }
        return UsernamePasswordAuthenticationToken.authenticated(
                jwtTokenProvider.getUsername(token),
                token,
                java.util.List.of());
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "bearer ", 0, 7)) {
            return null;
        }
        return authorization.substring(7);
    }

    private boolean requiresAuthentication(StompCommand command) {
        return StompCommand.SEND.equals(command) || StompCommand.SUBSCRIBE.equals(command);
    }
}
