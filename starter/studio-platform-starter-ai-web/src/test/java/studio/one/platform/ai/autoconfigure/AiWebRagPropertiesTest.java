package studio.one.platform.ai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class AiWebRagPropertiesTest {

    @Test
    void shouldExposeDiagnosticsDefaults() {
        AiWebRagProperties properties = new AiWebRagProperties();

        assertThat(properties.getDiagnostics().isAllowClientDebug()).isFalse();
    }

    @Test
    void shouldBindDiagnosticsOverrides() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "studio.ai.endpoints.rag.diagnostics.allow-client-debug", "true")));

        AiWebRagProperties properties = new Binder(ConfigurationPropertySources.get(environment))
                .bind("studio.ai.endpoints.rag", Bindable.of(AiWebRagProperties.class))
                .orElseThrow(() -> new AssertionError("AiWebRagProperties binding failed"));

        assertThat(properties.getDiagnostics().isAllowClientDebug()).isTrue();
    }
}
