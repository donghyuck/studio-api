package studio.one.platform.realtime.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import studio.one.platform.realtime.stomp.security.RealtimeHandshakeHandler;
import studio.one.platform.realtime.stomp.security.RealtimeStompDestinationAuthorizer;

class RealtimeStompAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RealtimeStompAutoConfiguration.class))
            .withBean(SimpMessagingTemplate.class, () -> new SimpMessagingTemplate(new MessageChannel() {
                @Override
                public boolean send(Message<?> message) {
                    return true;
                }

                @Override
                public boolean send(Message<?> message, long timeout) {
                    return true;
                }
            }))
            .withPropertyValues(
                    "studio.features.realtime.enabled=true",
                    "studio.realtime.stomp.enabled=true");

    @Test
    void failsFastWhenJwtIsEnabledWithoutTokenProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("requires a JwtTokenProvider");
        });
    }

    @Test
    void createsHandshakeHandlerWhenJwtIsDisabledWithoutTokenProvider() {
        contextRunner
                .withPropertyValues("studio.realtime.stomp.jwt-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RealtimeHandshakeHandler.class);
                    assertThat(context).hasSingleBean(RealtimeStompDestinationAuthorizer.class);
                });
    }

    @Test
    void createsDestinationAuthorizerWithoutEndpointAuthzBean() {
        contextRunner
                .withPropertyValues("studio.realtime.stomp.jwt-enabled=false")
                .run(context -> {
                    RealtimeStompDestinationAuthorizer authorizer =
                            context.getBean(RealtimeStompDestinationAuthorizer.class);

                    assertThat(authorizer.isAllowed(null,
                            org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE,
                            "/topic/mail-sync")).isFalse();
                });
    }

    @Test
    void createsJwtDisabledBeansWhenSpringSecurityCoreIsAbsent() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.security"))
                .withPropertyValues("studio.realtime.stomp.jwt-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RealtimeHandshakeHandler.class);
                    assertThat(context).hasSingleBean(RealtimeStompDestinationAuthorizer.class);
                });
    }
}
