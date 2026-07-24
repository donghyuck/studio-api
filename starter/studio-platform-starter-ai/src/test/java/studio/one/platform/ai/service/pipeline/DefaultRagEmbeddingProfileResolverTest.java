package studio.one.platform.ai.service.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.rag.RagEmbeddingProfile;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.model.ModelDeployment;
import studio.one.platform.ai.model.ModelDeploymentRegistry;
import studio.one.platform.ai.model.ModelWorkload;
import studio.one.platform.ai.model.catalog.BuiltInModelCatalog;

class DefaultRagEmbeddingProfileResolverTest {

    @Test
    void deploymentSelectionProducesCanonicalSpaceMetadata() {
        EmbeddingPort defaultPort = mock(EmbeddingPort.class);
        EmbeddingPort deploymentPort = mock(EmbeddingPort.class);
        ModelDeploymentRegistry registry = mock(ModelDeploymentRegistry.class);
        var definition = BuiltInModelCatalog.load().find("google/gemini-embedding-001").orElseThrow();
        var deployment = new ModelDeployment(
                "humanities-text-v1", "google-embedding", definition, ModelWorkload.EMBEDDING, 768, true);
        when(registry.find("humanities-text-v1")).thenReturn(java.util.Optional.of(deployment));
        when(registry.embeddingPort("humanities-text-v1")).thenReturn(deploymentPort);
        DefaultRagEmbeddingProfileResolver resolver = new DefaultRagEmbeddingProfileResolver(
                defaultPort, registry, null, Map.of());

        ResolvedRagEmbedding resolved = resolver.resolve(new RagEmbeddingSelection(
                null, null, null, null, EmbeddingInputType.TEXT, "humanities-text-v1"));

        assertThat(resolved.embeddingPort()).isSameAs(deploymentPort);
        assertThat(resolved.deploymentId()).isEqualTo("humanities-text-v1");
        assertThat(resolved.catalogId()).isEqualTo("google/gemini-embedding-001");
        assertThat(resolved.embeddingSpaceId()).startsWith("es:v1:");
        assertThat(resolved.metadata())
                .containsEntry("embeddingDeploymentId", "humanities-text-v1")
                .containsEntry("embeddingCatalogId", "google/gemini-embedding-001")
                .containsEntry("embeddingContractVersion", "v1")
                .containsEntry("embeddingSpaceIdV2", resolved.embeddingSpaceId());

        ResolvedRagEmbedding structuredText = resolver.resolve(new RagEmbeddingSelection(
                null, null, null, null, EmbeddingInputType.TABLE_TEXT, "humanities-text-v1"));
        assertThat(structuredText.embeddingSpaceId()).isEqualTo(resolved.embeddingSpaceId());
    }

    @Test
    void legacyCatalogIdResolvesToUniqueDeployment() {
        EmbeddingPort defaultPort = mock(EmbeddingPort.class);
        EmbeddingPort deploymentPort = mock(EmbeddingPort.class);
        ModelDeploymentRegistry registry = mock(ModelDeploymentRegistry.class);
        var definition = BuiltInModelCatalog.load().find("google/gemini-embedding-2").orElseThrow();
        var deployment = new ModelDeployment(
                "document-multimodal-v1", "google-embedding", definition,
                ModelWorkload.EMBEDDING, 768, true);
        when(registry.find("google/gemini-embedding-2")).thenReturn(java.util.Optional.empty());
        when(registry.deployments(ModelWorkload.EMBEDDING)).thenReturn(List.of(deployment));
        when(registry.embeddingPort("document-multimodal-v1")).thenReturn(deploymentPort);
        DefaultRagEmbeddingProfileResolver resolver = new DefaultRagEmbeddingProfileResolver(
                defaultPort, registry, null, Map.of());

        ResolvedRagEmbedding resolved = resolver.resolve(new RagEmbeddingSelection(
                null, null, null, null, EmbeddingInputType.TEXT, "google/gemini-embedding-2"));

        assertThat(resolved.embeddingPort()).isSameAs(deploymentPort);
        assertThat(resolved.deploymentId()).isEqualTo("document-multimodal-v1");
        assertThat(resolved.model()).isEqualTo("gemini-embedding-2");
    }

    @Test
    void explicitUnknownProfileDoesNotFallBackToDefaultEmbedding() {
        EmbeddingPort defaultPort = mock(EmbeddingPort.class);
        DefaultRagEmbeddingProfileResolver resolver = new DefaultRagEmbeddingProfileResolver(
                defaultPort,
                new AiProviderRegistry("default", Map.of(), Map.of("default", defaultPort)),
                null,
                Map.of());

        assertThatThrownBy(() -> resolver.resolve(new RagEmbeddingSelection(
                "missing-profile", null, null, EmbeddingInputType.TEXT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown RAG embedding profile");
    }

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

    @Test
    void profileRejectsMismatchedRequestedDimension() {
        EmbeddingPort defaultPort = mock(EmbeddingPort.class);
        DefaultRagEmbeddingProfileResolver resolver = new DefaultRagEmbeddingProfileResolver(
                defaultPort,
                new AiProviderRegistry("default", Map.of(), Map.of("default", defaultPort)),
                "retrieval",
                Map.of("retrieval", new RagEmbeddingProfile(
                        "retrieval", null, "text-model", 768,
                        List.of(EmbeddingInputType.TEXT), Map.of())));

        assertThatThrownBy(() -> resolver.resolve(new RagEmbeddingSelection(
                "retrieval", null, null, 1024, EmbeddingInputType.TEXT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected 768")
                .hasMessageContaining("requested 1024");
    }

    @Test
    void legacyAliasResolvesToCanonicalModelAndSpaceMetadata() {
        EmbeddingPort defaultPort = mock(EmbeddingPort.class);
        EmbeddingPort googlePort = mock(EmbeddingPort.class);
        RagEmbeddingProfile canonical = new RagEmbeddingProfile(
                "google-ai/gemini-embedding-001@768",
                "google-ai-gemini-embedding-001",
                "gemini-embedding-001",
                768,
                List.of(EmbeddingInputType.TEXT),
                Map.of(
                        "providerId", "google-ai",
                        "embeddingSpaceId", "google-ai/gemini-embedding-001@768"));
        DefaultRagEmbeddingProfileResolver resolver = new DefaultRagEmbeddingProfileResolver(
                defaultPort,
                new AiProviderRegistry("default", Map.of(), Map.of(
                        "default", defaultPort,
                        "google-ai-gemini-embedding-001", googlePort)),
                "google-ai/gemini-embedding-001@768",
                Map.of(
                        "google-ai/gemini-embedding-001@768", canonical,
                        "gemini-768", canonical));

        ResolvedRagEmbedding resolved = resolver.resolve(new RagEmbeddingSelection(
                "gemini-768", null, null, EmbeddingInputType.TEXT));

        assertThat(resolved.embeddingPort()).isSameAs(googlePort);
        assertThat(resolved.profileId()).isEqualTo("google-ai/gemini-embedding-001@768");
        assertThat(resolved.modelId()).isEqualTo("google-ai/gemini-embedding-001@768");
        assertThat(resolved.embeddingSpaceId()).isEqualTo("google-ai/gemini-embedding-001@768");
        assertThat(resolved.provider()).isEqualTo("google-ai");
        assertThat(resolved.metadata())
                .containsEntry("embeddingModelId", "google-ai/gemini-embedding-001@768")
                .containsEntry("embeddingSpaceId", "google-ai/gemini-embedding-001@768");
    }

    @Test
    void legacyProfileCanDelegateToCanonicalDeploymentWithoutDuplicatingModelMetadata() {
        EmbeddingPort defaultPort = mock(EmbeddingPort.class);
        EmbeddingPort deploymentPort = mock(EmbeddingPort.class);
        ModelDeploymentRegistry registry = mock(ModelDeploymentRegistry.class);
        var definition = BuiltInModelCatalog.load().find("google/gemini-embedding-001").orElseThrow();
        var deployment = new ModelDeployment(
                "humanities-text-v1", "google-ai", definition, ModelWorkload.EMBEDDING, 768, true);
        when(registry.find("humanities-text-v1")).thenReturn(java.util.Optional.of(deployment));
        when(registry.embeddingPort("humanities-text-v1")).thenReturn(deploymentPort);
        RagEmbeddingProfile compatibilityProfile = new RagEmbeddingProfile(
                "google-ai/gemini-embedding-001@768", null, null, null, null,
                Map.of("deploymentId", "humanities-text-v1"));
        DefaultRagEmbeddingProfileResolver resolver = new DefaultRagEmbeddingProfileResolver(
                defaultPort, registry, "google-ai/gemini-embedding-001@768",
                Map.of("google-ai/gemini-embedding-001@768", compatibilityProfile));

        ResolvedRagEmbedding resolved = resolver.resolve(new RagEmbeddingSelection(
                null, null, null, null, EmbeddingInputType.TEXT, null));

        assertThat(resolved.embeddingPort()).isSameAs(deploymentPort);
        assertThat(resolved.deploymentId()).isEqualTo("humanities-text-v1");
        assertThat(resolved.model()).isEqualTo("gemini-embedding-001");
        assertThat(resolved.dimension()).isEqualTo(768);
    }
}
