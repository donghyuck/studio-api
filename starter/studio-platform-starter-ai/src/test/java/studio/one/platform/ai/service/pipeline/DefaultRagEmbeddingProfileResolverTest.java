package studio.one.platform.ai.service.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.rag.RagEmbeddingProfile;
import studio.one.platform.ai.core.registry.AiProviderRegistry;

class DefaultRagEmbeddingProfileResolverTest {

    @Test
    void requestProviderAndModelDoNotInheritDefaultProfile() {
        EmbeddingPort defaultPort = mock(EmbeddingPort.class);
        EmbeddingPort googlePort = mock(EmbeddingPort.class);
        DefaultRagEmbeddingProfileResolver resolver = new DefaultRagEmbeddingProfileResolver(
                defaultPort,
                new AiProviderRegistry("default", Map.of(), Map.of("default", defaultPort, "google", googlePort)),
                "default-rag",
                Map.of("default-rag", new RagEmbeddingProfile(
                        "default-rag",
                        "default",
                        "default-model",
                        768,
                        List.of(EmbeddingInputType.TEXT),
                        Map.of())));

        ResolvedRagEmbedding resolved = resolver.resolve(new RagEmbeddingSelection(
                null,
                "google",
                "gemini-embedding-001",
                EmbeddingInputType.TEXT));

        assertThat(resolved.embeddingPort()).isSameAs(googlePort);
        assertThat(resolved.profileId()).isNull();
        assertThat(resolved.provider()).isEqualTo("google");
        assertThat(resolved.model()).isEqualTo("gemini-embedding-001");
        assertThat(resolved.dimension()).isNull();
    }

    @Test
    void legacyDefaultSelectionUsesConfiguredDefaultProfile() {
        EmbeddingPort defaultPort = mock(EmbeddingPort.class);
        EmbeddingPort googlePort = mock(EmbeddingPort.class);
        DefaultRagEmbeddingProfileResolver resolver = new DefaultRagEmbeddingProfileResolver(
                defaultPort,
                new AiProviderRegistry("default", Map.of(), Map.of("default", defaultPort, "google", googlePort)),
                "retrieval",
                Map.of("retrieval", new RagEmbeddingProfile(
                        "retrieval",
                        "google",
                        "gemini-embedding-001",
                        768,
                        List.of(EmbeddingInputType.TEXT),
                        Map.of())));

        ResolvedRagEmbedding resolved = resolver.resolve(new RagEmbeddingSelection(
                null,
                null,
                null,
                EmbeddingInputType.TEXT));

        assertThat(resolved.embeddingPort()).isSameAs(googlePort);
        assertThat(resolved.profileId()).isEqualTo("retrieval");
        assertThat(resolved.provider()).isEqualTo("google");
        assertThat(resolved.model()).isEqualTo("gemini-embedding-001");
        assertThat(resolved.dimension()).isEqualTo(768);
    }

    @Test
    void profileSelectionRejectsRequestProviderAndModelOverride() {
        EmbeddingPort defaultPort = mock(EmbeddingPort.class);
        DefaultRagEmbeddingProfileResolver resolver = new DefaultRagEmbeddingProfileResolver(
                defaultPort,
                new AiProviderRegistry("default", Map.of(), Map.of("default", defaultPort)),
                "retrieval",
                Map.of("retrieval", new RagEmbeddingProfile(
                        "retrieval",
                        null,
                        "gemini-embedding-001",
                        768,
                        List.of(EmbeddingInputType.TEXT),
                        Map.of())));

        assertThatThrownBy(() -> resolver.resolve(new RagEmbeddingSelection(
                "retrieval",
                "google",
                "other-model",
                EmbeddingInputType.TEXT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be supplied with embeddingProfileId");
    }

    @Test
    void profileWithoutExplicitInputTypesSupportsTextDerivedStructuredChunks() {
        EmbeddingPort defaultPort = mock(EmbeddingPort.class);
        RagEmbeddingProfile profile = new RagEmbeddingProfile(
                "retrieval",
                null,
                "gemini-embedding-001",
                768,
                null,
                Map.of());
        DefaultRagEmbeddingProfileResolver resolver = new DefaultRagEmbeddingProfileResolver(
                defaultPort,
                new AiProviderRegistry("default", Map.of(), Map.of("default", defaultPort)),
                "retrieval",
                Map.of("retrieval", profile));

        ResolvedRagEmbedding resolved = resolver.resolve(new RagEmbeddingSelection(
                "retrieval",
                null,
                null,
                EmbeddingInputType.TABLE_TEXT));

        assertThat(resolved.inputType()).isEqualTo(EmbeddingInputType.TABLE_TEXT);
    }

    @Test
    void profileRejectsUnsupportedInputTypesWhenConfiguredNarrowly() {
        EmbeddingPort defaultPort = mock(EmbeddingPort.class);
        DefaultRagEmbeddingProfileResolver resolver = new DefaultRagEmbeddingProfileResolver(
                defaultPort,
                new AiProviderRegistry("default", Map.of(), Map.of("default", defaultPort)),
                "retrieval",
                Map.of("retrieval", new RagEmbeddingProfile(
                        "retrieval",
                        null,
                        "text-only",
                        768,
                        List.of(EmbeddingInputType.TEXT),
                        Map.of())));

        assertThatThrownBy(() -> resolver.resolve(new RagEmbeddingSelection(
                "retrieval",
                null,
                null,
                EmbeddingInputType.IMAGE_CAPTION)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not support input type IMAGE_CAPTION");
    }
}
