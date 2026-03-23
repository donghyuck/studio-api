package studio.one.platform.realtime.stomp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RealtimeStompPropertiesTest {

    @Test
    void defaultsDenyAnonymousCrossOriginAccess() {
        RealtimeStompProperties properties = new RealtimeStompProperties();

        assertThat(properties.getAllowedOrigins()).isEmpty();
        assertThat(properties.isJwtEnabled()).isTrue();
        assertThat(properties.isRejectAnonymous()).isTrue();
    }
}
