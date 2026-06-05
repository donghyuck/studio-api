package studio.one.platform.realtime.stomp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.server.HandshakeFailureException;

import studio.one.base.security.jwt.JwtTokenProvider;
import studio.one.platform.realtime.stomp.config.RealtimeStompProperties;

class RealtimeStompAuthChannelInterceptorTest {

    @Test
    void authenticatesConnectFrameWithBearerToken() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getUsername("valid-token")).thenReturn("admin");
        RealtimeStompAuthChannelInterceptor interceptor = interceptor(tokenProvider);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> authenticated = interceptor.preSend(message, mock(MessageChannel.class));
        StompHeaderAccessor authenticatedAccessor = StompHeaderAccessor.wrap(authenticated);

        assertThat(authenticatedAccessor.getUser()).isNotNull();
        assertThat(authenticatedAccessor.getUser().getName()).isEqualTo("admin");
    }

    @Test
    void rejectsConnectFrameWithoutBearerToken() {
        RealtimeStompAuthChannelInterceptor interceptor = interceptor(mock(JwtTokenProvider.class));
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(HandshakeFailureException.class)
                .hasMessageContaining("STOMP CONNECT");
    }

    private RealtimeStompAuthChannelInterceptor interceptor(JwtTokenProvider tokenProvider) {
        RealtimeStompProperties properties = new RealtimeStompProperties();
        properties.setJwtEnabled(true);
        return new RealtimeStompAuthChannelInterceptor(properties, tokenProvider);
    }
}
