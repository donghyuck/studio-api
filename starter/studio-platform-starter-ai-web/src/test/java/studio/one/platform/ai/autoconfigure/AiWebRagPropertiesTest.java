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
    void shouldExposeContextExpansionAndDiagnosticsDefaults() {
        AiWebRagProperties properties = new AiWebRagProperties();

        assertThat(properties.getContext().getExpansion().isEnabled()).isTrue();
        assertThat(properties.getContext().getExpansion().getCandidateMultiplier()).isEqualTo(4);
        assertThat(properties.getContext().getExpansion().getMaxCandidates()).isEqualTo(100);
        assertThat(properties.getContext().getExpansion().getPreviousWindow()).isEqualTo(1);
        assertThat(properties.getContext().getExpansion().getNextWindow()).isEqualTo(1);
        assertThat(properties.getContext().getExpansion().isIncludeParentContent()).isTrue();
        assertThat(properties.getChunkPreview().isEnabled()).isTrue();
        assertThat(properties.getChunkPreview().getMaxInputChars()).isEqualTo(200_000);
        assertThat(properties.getChunkPreview().getMaxPreviewChunks()).isEqualTo(500);
        assertThat(properties.getDiagnostics().isAllowClientDebug()).isFalse();
    }

    @Test
    void shouldBindContextExpansionAndDiagnosticsOverrides() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.ofEntries(
                Map.entry("studio.ai.endpoints.rag.context.expansion.enabled", "false"),
                Map.entry("studio.ai.endpoints.rag.context.expansion.candidate-multiplier", "6"),
                Map.entry("studio.ai.endpoints.rag.context.expansion.max-candidates", "30"),
                Map.entry("studio.ai.endpoints.rag.context.expansion.previous-window", "2"),
                Map.entry("studio.ai.endpoints.rag.context.expansion.next-window", "3"),
                Map.entry("studio.ai.endpoints.rag.context.expansion.include-parent-content", "false"),
                Map.entry("studio.ai.endpoints.rag.chunk-preview.enabled", "false"),
                Map.entry("studio.ai.endpoints.rag.chunk-preview.max-input-chars", "1000"),
                Map.entry("studio.ai.endpoints.rag.chunk-preview.max-preview-chunks", "25"),
                Map.entry("studio.ai.endpoints.rag.diagnostics.allow-client-debug", "true"))));

        AiWebRagProperties properties = new Binder(ConfigurationPropertySources.get(environment))
                .bind("studio.ai.endpoints.rag", Bindable.of(AiWebRagProperties.class))
                .orElseThrow(() -> new AssertionError("AiWebRagProperties binding failed"));

        assertThat(properties.getContext().getExpansion().isEnabled()).isFalse();
        assertThat(properties.getContext().getExpansion().getCandidateMultiplier()).isEqualTo(6);
        assertThat(properties.getContext().getExpansion().getMaxCandidates()).isEqualTo(30);
        assertThat(properties.getContext().getExpansion().getPreviousWindow()).isEqualTo(2);
        assertThat(properties.getContext().getExpansion().getNextWindow()).isEqualTo(3);
        assertThat(properties.getContext().getExpansion().isIncludeParentContent()).isFalse();
        assertThat(properties.getChunkPreview().isEnabled()).isFalse();
        assertThat(properties.getChunkPreview().getMaxInputChars()).isEqualTo(1000);
        assertThat(properties.getChunkPreview().getMaxPreviewChunks()).isEqualTo(25);
        assertThat(properties.getDiagnostics().isAllowClientDebug()).isTrue();
    }
}
