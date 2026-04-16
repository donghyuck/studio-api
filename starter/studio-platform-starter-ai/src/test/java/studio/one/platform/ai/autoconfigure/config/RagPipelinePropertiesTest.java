package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class RagPipelinePropertiesTest {

    @Test
    void shouldExposeRetrievalAndObjectScopeDefaults() {
        RagPipelineProperties properties = new RagPipelineProperties();

        assertThat(properties.getRetrieval().getVectorWeight()).isEqualTo(0.7d);
        assertThat(properties.getRetrieval().getLexicalWeight()).isEqualTo(0.3d);
        assertThat(properties.getRetrieval().getMinRelevanceScore()).isEqualTo(0.15d);
        assertThat(properties.getRetrieval().isKeywordFallbackEnabled()).isTrue();
        assertThat(properties.getRetrieval().isSemanticFallbackEnabled()).isTrue();
        assertThat(properties.getObjectScope().getDefaultListLimit()).isEqualTo(20);
        assertThat(properties.getObjectScope().getMaxListLimit()).isEqualTo(100);
    }

    @Test
    void shouldBindRetrievalAndObjectScopeOverrides() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
                "studio.ai.pipeline.retrieval.vector-weight", "0.2",
                "studio.ai.pipeline.retrieval.lexical-weight", "0.8",
                "studio.ai.pipeline.retrieval.min-relevance-score", "0.4",
                "studio.ai.pipeline.retrieval.keyword-fallback-enabled", "false",
                "studio.ai.pipeline.retrieval.semantic-fallback-enabled", "false",
                "studio.ai.pipeline.object-scope.default-list-limit", "5",
                "studio.ai.pipeline.object-scope.max-list-limit", "10")));

        RagPipelineProperties properties = new Binder(ConfigurationPropertySources.get(environment))
                .bind("studio.ai.pipeline", Bindable.of(RagPipelineProperties.class))
                .orElseThrow(() -> new AssertionError("RagPipelineProperties binding failed"));

        assertThat(properties.getRetrieval().getVectorWeight()).isEqualTo(0.2d);
        assertThat(properties.getRetrieval().getLexicalWeight()).isEqualTo(0.8d);
        assertThat(properties.getRetrieval().getMinRelevanceScore()).isEqualTo(0.4d);
        assertThat(properties.getRetrieval().isKeywordFallbackEnabled()).isFalse();
        assertThat(properties.getRetrieval().isSemanticFallbackEnabled()).isFalse();
        assertThat(properties.getObjectScope().getDefaultListLimit()).isEqualTo(5);
        assertThat(properties.getObjectScope().getMaxListLimit()).isEqualTo(10);
    }
}
