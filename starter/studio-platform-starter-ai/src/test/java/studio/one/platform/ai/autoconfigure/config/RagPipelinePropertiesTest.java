package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Method;
import java.util.Map;

@SuppressWarnings("deprecation")
class RagPipelinePropertiesTest {

    @Test
    void shouldExposeRetrievalAndObjectScopeDefaults() {
        RagPipelineProperties properties = new RagPipelineProperties();

        assertThat(properties.getChunkSize()).isEqualTo(500);
        assertThat(properties.getChunkOverlap()).isEqualTo(50);
        assertThat(properties.getRetrieval().getVectorWeight()).isEqualTo(0.7d);
        assertThat(properties.getRetrieval().getLexicalWeight()).isEqualTo(0.3d);
        assertThat(properties.getRetrieval().getMinRelevanceScore()).isEqualTo(0.15d);
        assertThat(properties.getRetrieval().isKeywordFallbackEnabled()).isTrue();
        assertThat(properties.getRetrieval().isSemanticFallbackEnabled()).isTrue();
        assertThat(properties.getRetrieval().getQueryExpansion().isEnabled()).isTrue();
        assertThat(properties.getRetrieval().getQueryExpansion().getMaxKeywords()).isEqualTo(10);
        assertThat(properties.getObjectScope().getDefaultListLimit()).isEqualTo(20);
        assertThat(properties.getObjectScope().getMaxListLimit()).isEqualTo(100);
        assertThat(properties.getKeywords().getScope()).isEqualTo("document");
        assertThat(properties.getKeywords().getMaxInputChars()).isEqualTo(4_000);
        assertThat(properties.getCleaner().isEnabled()).isFalse();
        assertThat(properties.getCleaner().getPrompt()).isEqualTo("rag-cleaner");
        assertThat(properties.getCleaner().getMaxInputChars()).isEqualTo(20_000);
        assertThat(properties.getCleaner().isFailOpen()).isTrue();
        assertThat(properties.getDiagnostics().isEnabled()).isFalse();
        assertThat(properties.getDiagnostics().isLogResults()).isFalse();
        assertThat(properties.getDiagnostics().getMaxSnippetChars()).isEqualTo(120);
    }

    @Test
    void shouldBindRetrievalAndObjectScopeOverrides() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.ofEntries(
                Map.entry("studio.ai.pipeline.retrieval.vector-weight", "0.2"),
                Map.entry("studio.ai.pipeline.retrieval.lexical-weight", "0.8"),
                Map.entry("studio.ai.pipeline.retrieval.min-relevance-score", "0.4"),
                Map.entry("studio.ai.pipeline.retrieval.keyword-fallback-enabled", "false"),
                Map.entry("studio.ai.pipeline.retrieval.semantic-fallback-enabled", "false"),
                Map.entry("studio.ai.pipeline.retrieval.query-expansion.enabled", "false"),
                Map.entry("studio.ai.pipeline.retrieval.query-expansion.max-keywords", "4"),
                Map.entry("studio.ai.pipeline.object-scope.default-list-limit", "5"),
                Map.entry("studio.ai.pipeline.object-scope.max-list-limit", "10"),
                Map.entry("studio.ai.pipeline.keywords.scope", "both"),
                Map.entry("studio.ai.pipeline.keywords.max-input-chars", "2048"),
                Map.entry("studio.ai.pipeline.cleaner.enabled", "true"),
                Map.entry("studio.ai.pipeline.cleaner.prompt", "custom-cleaner"),
                Map.entry("studio.ai.pipeline.cleaner.max-input-chars", "1234"),
                Map.entry("studio.ai.pipeline.cleaner.fail-open", "false"),
                Map.entry("studio.ai.pipeline.diagnostics.enabled", "true"),
                Map.entry("studio.ai.pipeline.diagnostics.log-results", "true"),
                Map.entry("studio.ai.pipeline.diagnostics.max-snippet-chars", "42"))));

        RagPipelineProperties properties = new Binder(ConfigurationPropertySources.get(environment))
                .bind("studio.ai.pipeline", Bindable.of(RagPipelineProperties.class))
                .orElseThrow(() -> new AssertionError("RagPipelineProperties binding failed"));

        assertThat(properties.getRetrieval().getVectorWeight()).isEqualTo(0.2d);
        assertThat(properties.getRetrieval().getLexicalWeight()).isEqualTo(0.8d);
        assertThat(properties.getRetrieval().getMinRelevanceScore()).isEqualTo(0.4d);
        assertThat(properties.getRetrieval().isKeywordFallbackEnabled()).isFalse();
        assertThat(properties.getRetrieval().isSemanticFallbackEnabled()).isFalse();
        assertThat(properties.getRetrieval().getQueryExpansion().isEnabled()).isFalse();
        assertThat(properties.getRetrieval().getQueryExpansion().getMaxKeywords()).isEqualTo(4);
        assertThat(properties.getObjectScope().getDefaultListLimit()).isEqualTo(5);
        assertThat(properties.getObjectScope().getMaxListLimit()).isEqualTo(10);
        assertThat(properties.getKeywords().getScope()).isEqualTo("both");
        assertThat(properties.getKeywords().getMaxInputChars()).isEqualTo(2048);
        assertThat(properties.getCleaner().isEnabled()).isTrue();
        assertThat(properties.getCleaner().getPrompt()).isEqualTo("custom-cleaner");
        assertThat(properties.getCleaner().getMaxInputChars()).isEqualTo(1234);
        assertThat(properties.getCleaner().isFailOpen()).isFalse();
        assertThat(properties.getDiagnostics().isEnabled()).isTrue();
        assertThat(properties.getDiagnostics().isLogResults()).isTrue();
        assertThat(properties.getDiagnostics().getMaxSnippetChars()).isEqualTo(42);
    }

    @Test
    void shouldBindLegacyChunkFallbackDefaultsAndOverrides() {
        RagPipelineProperties defaults = new RagPipelineProperties();

        assertThat(defaults.getChunkSize()).isEqualTo(500);
        assertThat(defaults.getChunkOverlap()).isEqualTo(50);

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                RagPipelineProperties.LEGACY_CHUNK_SIZE_PROPERTY, "900",
                RagPipelineProperties.LEGACY_CHUNK_OVERLAP_PROPERTY, "90")));

        RagPipelineProperties properties = new Binder(ConfigurationPropertySources.get(environment))
                .bind("studio.ai.pipeline", Bindable.of(RagPipelineProperties.class))
                .orElseThrow(() -> new AssertionError("RagPipelineProperties binding failed"));

        assertThat(properties.getChunkSize()).isEqualTo(900);
        assertThat(properties.getChunkOverlap()).isEqualTo(90);
    }

    @Test
    void shouldMarkLegacyChunkFallbackAccessorsAsDeprecated() throws NoSuchMethodException {
        assertDeprecatedFallbackAccessor(RagPipelineProperties.class.getMethod("getChunkSize"));
        assertDeprecatedFallbackAccessor(RagPipelineProperties.class.getMethod("setChunkSize", int.class));
        assertDeprecatedFallbackAccessor(RagPipelineProperties.class.getMethod("getChunkOverlap"));
        assertDeprecatedFallbackAccessor(RagPipelineProperties.class.getMethod("setChunkOverlap", int.class));
    }

    @Test
    void shouldExposeVectorStoreDefaultsAndOverrides() {
        VectorStoreProperties defaults = new VectorStoreProperties();
        assertThat(defaults.getPostgres().getTextSearchConfig()).isEqualTo("simple");

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "studio.ai.vector.postgres.text-search-config", "simple")));

        VectorStoreProperties properties = new Binder(ConfigurationPropertySources.get(environment))
                .bind("studio.ai.vector", Bindable.of(VectorStoreProperties.class))
                .orElseThrow(() -> new AssertionError("VectorStoreProperties binding failed"));

        assertThat(properties.getPostgres().getTextSearchConfig()).isEqualTo("simple");
    }

    private void assertDeprecatedFallbackAccessor(Method method) {
        Deprecated deprecated = method.getAnnotation(Deprecated.class);

        assertThat(deprecated).isNotNull();
        assertThat(deprecated.since()).isEqualTo("2.x");
        assertThat(deprecated.forRemoval()).isFalse();
    }
}
