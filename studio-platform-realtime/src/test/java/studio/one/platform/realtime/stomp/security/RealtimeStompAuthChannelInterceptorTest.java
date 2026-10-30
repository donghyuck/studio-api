package studio.one.platform.realtime.stomp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.server.HandshakeFailureException;

import studio.one.base.security.jwt.JwtTokenProvider;
import studio.one.platform.realtime.stomp.config.RealtimeStompProperties;

class RealtimeStompAuthChannelInterceptorTest {

    @Test
    void authenticatesConnectFrameWithBearerToken() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        UserDetails details = User.withUsername("admin").password("n/a").roles("MAIL").build();
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(details, "", "ROLE_MAIL");
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getAuthentication(eq("valid-token"), any(UserDetailsService.class), eq(true)))
                .thenReturn(authentication);
        RealtimeStompAuthChannelInterceptor interceptor = interceptor(tokenProvider);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> authenticated = interceptor.preSend(message, mock(MessageChannel.class));
        StompHeaderAccessor authenticatedAccessor = StompHeaderAccessor.wrap(authenticated);

        assertThat(authenticatedAccessor.getUser()).isNotNull();
        assertThat(authenticatedAccessor.getUser().getName()).isEqualTo("admin");
        assertThat(((org.springframework.security.core.Authentication) authenticatedAccessor.getUser()).getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_MAIL");
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

    @Test
    void keepsTrustedConnectPrincipalWhenAuthorizationHeaderIsPresent() {
        RealtimeStompAuthChannelInterceptor interceptor = interceptor(mock(JwtTokenProvider.class));
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Principal trusted = new TestingAuthenticationToken("trusted", "n/a", "ROLE_USER");
        accessor.setUser(trusted);
        accessor.setNativeHeader("Authorization", "Bearer replacement");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        assertThat(StompHeaderAccessor.wrap(result).getUser()).isSameAs(trusted);
    }

    @Test
    void deniesSubscribeWhenAuthorizerIsMissing() {
        RealtimeStompAuthChannelInterceptor interceptor = new RealtimeStompAuthChannelInterceptor(
                jwtDisabledProperties(),
                null,
                null,
                null);
        Message<?> message = message(StompCommand.SUBSCRIBE, "/topic/mail-sync",
                new TestingAuthenticationToken("mail-user", "n/a", "ROLE_MAIL"));

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(HandshakeFailureException.class)
                .hasMessageContaining("destination");
    }

    @Test
    void authorizesSubscribeWithJwtDisabledTrustedPrincipal() {
        RealtimeStompAuthChannelInterceptor interceptor = new RealtimeStompAuthChannelInterceptor(
                jwtDisabledProperties(),
                null,
                null,
                (principal, command, destination) -> "/topic/mail-sync".equals(destination));
        Message<?> message = message(StompCommand.SUBSCRIBE, "/topic/mail-sync",
                new TestingAuthenticationToken("mail-user", "n/a", "ROLE_MAIL"));

        assertThat(interceptor.preSend(message, mock(MessageChannel.class))).isSameAs(message);
    }

    @Test
    void preservesConnectDisconnectAndHeartbeatFrames() {
        RealtimeStompAuthChannelInterceptor interceptor = new RealtimeStompAuthChannelInterceptor(
                jwtDisabledProperties(),
                null,
                null,
                (principal, command, destination) -> false);
        Message<?> connect = message(StompCommand.CONNECT, null, null);
        Message<?> disconnect = message(StompCommand.DISCONNECT, null, null);
        Message<?> heartbeat = MessageBuilder.withPayload(new byte[] { '\n' }).build();

        assertThat(interceptor.preSend(connect, mock(MessageChannel.class))).isSameAs(connect);
        assertThat(interceptor.preSend(disconnect, mock(MessageChannel.class))).isSameAs(disconnect);
        assertThat(interceptor.preSend(heartbeat, mock(MessageChannel.class))).isSameAs(heartbeat);
    }

    private RealtimeStompAuthChannelInterceptor interceptor(JwtTokenProvider tokenProvider) {
        RealtimeStompProperties properties = new RealtimeStompProperties();
        properties.setJwtEnabled(true);
        UserDetails details = User.withUsername("admin").password("n/a").roles("MAIL").build();
        UserDetailsService userDetailsService = username -> details;
        return new RealtimeStompAuthChannelInterceptor(properties, tokenProvider, userDetailsService, null);
    }

    private RealtimeStompProperties jwtDisabledProperties() {
        RealtimeStompProperties properties = new RealtimeStompProperties();
        properties.setJwtEnabled(false);
        return properties;
    }

    private Message<?> message(StompCommand command, String destination, Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
