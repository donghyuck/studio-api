package studio.one.platform.realtime.stomp.security;

import java.security.Principal;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.server.HandshakeFailureException;

import lombok.RequiredArgsConstructor;
import studio.one.platform.realtime.stomp.config.RealtimeStompProperties;

@RequiredArgsConstructor
public class RealtimeStompAuthChannelInterceptor implements ChannelInterceptor {

    private final RealtimeStompProperties properties;
    private final Object jwtTokenProvider;
    private final Object userDetailsService;
    private final RealtimeStompDestinationAuthorizer destinationAuthorizer;

    public RealtimeStompAuthChannelInterceptor(
            RealtimeStompProperties properties,
            Object jwtTokenProvider) {
        this(properties, jwtTokenProvider, null, null);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            if (!properties.isJwtEnabled() || accessor.getUser() != null) {
                return message;
            }
            StompHeaderAccessor mutableAccessor = StompHeaderAccessor.wrap(message);
            mutableAccessor.setUser(authenticate(mutableAccessor.getFirstNativeHeader("Authorization")));
            return MessageBuilder.createMessage(message.getPayload(), mutableAccessor.getMessageHeaders());
        }
        if (requiresAuthentication(accessor.getCommand()) && accessor.getUser() == null) {
            throw new HandshakeFailureException("Authenticated STOMP connection is required");
        }
        if (requiresAuthorization(accessor.getCommand())
                && (destinationAuthorizer == null
                        || !destinationAuthorizer.isAllowed(
                                accessor.getUser(),
                                accessor.getCommand(),
                                accessor.getDestination()))) {
            throw new HandshakeFailureException("STOMP destination is not authorized");
        }
        return message;
    }

    private Principal authenticate(String authorization) {
        String token = bearerToken(authorization);
        if (token == null || jwtTokenProvider == null || userDetailsService == null || !validateToken(token)) {
            throw new HandshakeFailureException("Valid JWT bearer token is required in STOMP CONNECT");
        }
        try {
            Class<?> userDetailsServiceType =
                    Class.forName("org.springframework.security.core.userdetails.UserDetailsService");
            Object authentication = jwtTokenProvider.getClass()
                    .getMethod("getAuthentication", String.class, userDetailsServiceType, boolean.class)
                    .invoke(jwtTokenProvider, token, userDetailsService, true);
            if (authentication instanceof Principal principal) {
                return principal;
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            throw new HandshakeFailureException("Valid JWT bearer token is required in STOMP CONNECT", ex);
        }
        throw new HandshakeFailureException("Valid JWT bearer token is required in STOMP CONNECT");
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

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "bearer ", 0, 7)) {
            return null;
        }
        return authorization.substring(7);
    }

    private boolean requiresAuthentication(StompCommand command) {
        return StompCommand.SEND.equals(command) || StompCommand.SUBSCRIBE.equals(command);
    }

    private boolean requiresAuthorization(StompCommand command) {
        return StompCommand.SEND.equals(command) || StompCommand.SUBSCRIBE.equals(command);
    }
}
