package studio.one.platform.ai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class AiWebChatPropertiesTest {

    @Test
    void shouldExposeChatMemoryDefaults() {
        AiWebChatProperties properties = new AiWebChatProperties();

        assertThat(properties.getMemory().isEnabled()).isFalse();
        assertThat(properties.getMemory().getMaxMessages()).isEqualTo(20);
        assertThat(properties.getMemory().getMaxConversations()).isEqualTo(1_000);
        assertThat(properties.getMemory().getTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void shouldBindChatMemoryOverrides() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "studio.ai.endpoints.chat.memory.enabled", "true",
                "studio.ai.endpoints.chat.memory.max-messages", "12",
                "studio.ai.endpoints.chat.memory.max-conversations", "200",
                "studio.ai.endpoints.chat.memory.ttl", "5m")));

        AiWebChatProperties properties = new Binder(ConfigurationPropertySources.get(environment))
                .bind("studio.ai.endpoints.chat", Bindable.of(AiWebChatProperties.class))
                .orElseThrow(() -> new AssertionError("AiWebChatProperties binding failed"));

        assertThat(properties.getMemory().isEnabled()).isTrue();
        assertThat(properties.getMemory().getMaxMessages()).isEqualTo(12);
        assertThat(properties.getMemory().getMaxConversations()).isEqualTo(200);
        assertThat(properties.getMemory().getTtl()).isEqualTo(Duration.ofMinutes(5));
    }
}
