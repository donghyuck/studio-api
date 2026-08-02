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

        assertThat(properties.getContext().getMaxChunkChars()).isEqualTo(2_000);
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
        assertThat(properties.getAnswerPolicy().getDefaultMode())
                .isEqualTo(studio.one.platform.ai.web.controller.RagAnswerMode.GROUNDED_INFERENCE);
        assertThat(properties.getAnswerPolicy().getMaximumMode())
                .isEqualTo(studio.one.platform.ai.web.controller.RagAnswerMode.GROUNDED_INFERENCE);
        assertThat(properties.getAnswerPolicy().isClientSelectionEnabled()).isTrue();
        assertThat(properties.getAnswerPolicy().isFactualListPartialAnswerEnabled()).isFalse();
        assertThat(properties.getSourcePolicy().getDefaultScope())
                .isEqualTo(studio.one.platform.ai.web.controller.RagSourceScope.DOCUMENT_ONLY);
        assertThat(properties.getSourcePolicy().isClientSelectionEnabled()).isFalse();
        assertThat(properties.getExternalSources().isEnabled()).isFalse();
        assertThat(properties.getExternalSources().getTimeout()).isEqualTo(java.time.Duration.ofSeconds(8));
        assertThat(properties.getExternalSources().getMaxResults()).isEqualTo(8);
    }

    @Test
    void shouldBindContextExpansionAndDiagnosticsOverrides() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.ofEntries(
                Map.entry("studio.ai.endpoints.rag.context.max-chunk-chars", "1200"),
                Map.entry("studio.ai.endpoints.rag.context.expansion.enabled", "false"),
                Map.entry("studio.ai.endpoints.rag.context.expansion.candidate-multiplier", "6"),
                Map.entry("studio.ai.endpoints.rag.context.expansion.max-candidates", "30"),
                Map.entry("studio.ai.endpoints.rag.context.expansion.previous-window", "2"),
                Map.entry("studio.ai.endpoints.rag.context.expansion.next-window", "3"),
                Map.entry("studio.ai.endpoints.rag.context.expansion.include-parent-content", "false"),
                Map.entry("studio.ai.endpoints.rag.chunk-preview.enabled", "false"),
                Map.entry("studio.ai.endpoints.rag.chunk-preview.max-input-chars", "1000"),
                Map.entry("studio.ai.endpoints.rag.chunk-preview.max-preview-chunks", "25"),
                Map.entry("studio.ai.endpoints.rag.diagnostics.allow-client-debug", "true"),
                Map.entry("studio.ai.endpoints.rag.answer-policy.default-mode", "STRICT_GROUNDED"),
                Map.entry("studio.ai.endpoints.rag.answer-policy.maximum-mode", "STRICT_GROUNDED"),
                Map.entry("studio.ai.endpoints.rag.answer-policy.client-selection-enabled", "false"),
                Map.entry("studio.ai.endpoints.rag.answer-policy.factual-list-partial-answer-enabled", "true"),
                Map.entry("studio.ai.endpoints.rag.source-policy.client-selection-enabled", "true"),
                Map.entry("studio.ai.endpoints.rag.external-sources.enabled", "true"),
                Map.entry("studio.ai.endpoints.rag.external-sources.gateway-url",
                        "https://evidence.internal.example/api/search"),
                Map.entry("studio.ai.endpoints.rag.external-sources.api-key", "test-secret"),
                Map.entry("studio.ai.endpoints.rag.external-sources.gateway-allowed-hosts",
                        "evidence.internal.example"),
                Map.entry("studio.ai.endpoints.rag.external-sources.source-allowed-hosts",
                        "open.law.go.kr,law.go.kr"),
                Map.entry("studio.ai.endpoints.rag.external-sources.timeout", "3s"),
                Map.entry("studio.ai.endpoints.rag.external-sources.max-results", "5"))));

        AiWebRagProperties properties = new Binder(ConfigurationPropertySources.get(environment))
                .bind("studio.ai.endpoints.rag", Bindable.of(AiWebRagProperties.class))
                .orElseThrow(() -> new AssertionError("AiWebRagProperties binding failed"));

        assertThat(properties.getContext().getMaxChunkChars()).isEqualTo(1200);
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
        assertThat(properties.getAnswerPolicy().getDefaultMode())
                .isEqualTo(studio.one.platform.ai.web.controller.RagAnswerMode.STRICT_GROUNDED);
        assertThat(properties.getAnswerPolicy().getMaximumMode())
                .isEqualTo(studio.one.platform.ai.web.controller.RagAnswerMode.STRICT_GROUNDED);
        assertThat(properties.getAnswerPolicy().isClientSelectionEnabled()).isFalse();
        assertThat(properties.getAnswerPolicy().isFactualListPartialAnswerEnabled()).isTrue();
        assertThat(properties.getSourcePolicy().isClientSelectionEnabled()).isTrue();
        assertThat(properties.getExternalSources().isEnabled()).isTrue();
        assertThat(properties.getExternalSources().getGatewayUrl())
                .isEqualTo("https://evidence.internal.example/api/search");
        assertThat(properties.getExternalSources().getGatewayAllowedHosts())
                .containsExactly("evidence.internal.example");
        assertThat(properties.getExternalSources().getSourceAllowedHosts())
                .containsExactlyInAnyOrder("open.law.go.kr", "law.go.kr");
        assertThat(properties.getExternalSources().getTimeout()).isEqualTo(java.time.Duration.ofSeconds(3));
        assertThat(properties.getExternalSources().getMaxResults()).isEqualTo(5);
    }
}
