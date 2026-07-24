package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.autoconfigure.AiWebChatProperties;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.ai.model.ModelDefinition;
import studio.one.platform.ai.model.ModelDeployment;
import studio.one.platform.ai.model.ModelDeploymentRegistry;
import studio.one.platform.ai.model.ModelDimensionPolicy;
import studio.one.platform.ai.model.ModelWorkload;
import studio.one.platform.web.dto.ApiResponse;

class AiInfoControllerTest {

    @Test
    void addsDeploymentSummaryWithoutChangingLegacyProviderFields() {
        AiAdapterProperties properties = new AiAdapterProperties();
        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.getEmbedding().setEnabled(true);
        provider.getEmbedding().setModel("gemini-embedding-001");
        properties.getProviders().put("google-embedding", provider);
        ModelDefinition definition = mock(ModelDefinition.class);
        when(definition.supports(ModelWorkload.EMBEDDING)).thenReturn(true);
        when(definition.catalogId()).thenReturn("google/gemini-embedding-001");
        when(definition.providerFamily()).thenReturn("google");
        when(definition.apiModel()).thenReturn("gemini-embedding-001");
        when(definition.dimensionPolicy()).thenReturn(new ModelDimensionPolicy(Set.of(768), 768));
        ModelDeployment deployment = new ModelDeployment(
                "humanities-text-v1", "google-embedding", definition, ModelWorkload.EMBEDDING, 768, true);
        ModelDeploymentRegistry registry = mock(ModelDeploymentRegistry.class);
        when(registry.deployments(null)).thenReturn(List.of(deployment));
        when(registry.defaultDeployment(ModelWorkload.EMBEDDING)).thenReturn(Optional.of(deployment));
        when(registry.defaultDeployment(ModelWorkload.CHAT)).thenReturn(Optional.empty());

        AiInfoController controller = new AiInfoController(
                properties, new AiWebChatProperties(), new MockEnvironment(), null, registry);

        var response = controller.providers().getBody().getData();

        assertThat(response.providers()).singleElement().satisfies(info -> {
            assertThat(info.name()).isEqualTo("google-embedding");
            assertThat(info.embedding().model()).isEqualTo("gemini-embedding-001");
            assertThat(info.deployments()).extracting(AiInfoController.DeploymentSummary::deploymentId)
                    .containsExactly("humanities-text-v1");
        });
        assertThat(response.defaultEmbeddingDeployment()).isEqualTo("humanities-text-v1");
        assertThat(response.deployments()).singleElement().satisfies(summary -> {
            assertThat(summary.catalogId()).isEqualTo("google/gemini-embedding-001");
            assertThat(summary.embeddingSpaceId()).startsWith("es:v1:");
            assertThat(summary.providerStatus()).isEqualTo("UNVERIFIED");
        });
    }

    @Test
    void exposesSpringAiProviderModelsAsCanonicalSource() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("google-ai-gemini");

        AiAdapterProperties.Provider google = new AiAdapterProperties.Provider();
        google.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        google.getChat().setEnabled(true);
        google.getChat().setModel("legacy-google-chat");
        google.getEmbedding().setEnabled(true);
        google.getEmbedding().setModel("legacy-google-embedding");
        properties.getProviders().put("google-ai-gemini", google);

        AiAdapterProperties.Provider ollama = new AiAdapterProperties.Provider();
        ollama.setType(AiAdapterProperties.ProviderType.OLLAMA);
        ollama.getEmbedding().setEnabled(true);
        ollama.getEmbedding().setModel("legacy-ollama-embedding");
        properties.getProviders().put("ollama", ollama);

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.google.genai.chat.options.model", "gemini-2.5-flash")
                .withProperty("spring.ai.google.genai.embedding.text.options.model", "gemini-embedding-001")
                .withProperty("spring.ai.ollama.embedding.options.model", "nomic-embed-text");
        AiInfoController controller = new AiInfoController(
                properties,
                new AiWebChatProperties(),
                environment,
                null);

        ApiResponse<AiInfoController.AiInfoResponse> body = controller.providers().getBody();

        assertThat(body.getData().defaultProvider()).isEqualTo("google-ai-gemini");
        assertThat(body.getData().defaultChatProvider()).isEqualTo("google-ai-gemini");
        assertThat(body.getData().defaultEmbeddingProvider()).isEqualTo("google-ai-gemini");
        assertThat(body.getData().providers()).hasSize(2);
        AiInfoController.ProviderInfo googleInfo = body.getData().providers().get(0);
        assertThat(googleInfo.name()).isEqualTo("google-ai-gemini");
        assertThat(googleInfo.chat().model()).isEqualTo("gemini-2.5-flash");
        assertThat(googleInfo.embedding().model()).isEqualTo("gemini-embedding-001");
        AiInfoController.ProviderInfo ollamaInfo = body.getData().providers().get(1);
        assertThat(ollamaInfo.embedding().model()).isEqualTo("nomic-embed-text");
    }

    @Test
    void exposesActualModelForExplicitGoogleProviderOverrides() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultChatProvider("google-pro");
        properties.setDefaultEmbeddingProvider("google-embedding-2");

        AiAdapterProperties.Provider pro = new AiAdapterProperties.Provider();
        pro.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        pro.getChat().setEnabled(true);
        pro.getChat().setModel("gemini-2.5-pro");
        pro.getChat().setModelOverride(true);
        properties.getProviders().put("google-pro", pro);

        AiAdapterProperties.Provider embedding = new AiAdapterProperties.Provider();
        embedding.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        embedding.getEmbedding().setEnabled(true);
        embedding.getEmbedding().setModel("gemini-embedding-2");
        embedding.getEmbedding().setModelOverride(true);
        properties.getProviders().put("google-embedding-2", embedding);

        AiInfoController controller = new AiInfoController(
                properties,
                new AiWebChatProperties(),
                new MockEnvironment()
                        .withProperty("spring.ai.google.genai.chat.options.model", "gemini-2.5-flash")
                        .withProperty("spring.ai.google.genai.embedding.text.options.model", "gemini-embedding-001"),
                null);

        ApiResponse<AiInfoController.AiInfoResponse> body = controller.providers().getBody();

        AiInfoController.ProviderInfo proInfo = body.getData().providers().get(0);
        assertThat(proInfo.chat().model()).isEqualTo("gemini-2.5-pro");
        assertThat(proInfo.embedding().model()).isNull();
        AiInfoController.ProviderInfo embeddingInfo = body.getData().providers().get(1);
        assertThat(embeddingInfo.chat().model()).isNull();
        assertThat(embeddingInfo.embedding().model()).isEqualTo("gemini-embedding-2");
    }

    @Test
    void exposesOpenAiBaseUrlOnlyFromSpringAiCanonicalProperty() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("openai");

        AiAdapterProperties.Provider openai = new AiAdapterProperties.Provider();
        openai.setType(AiAdapterProperties.ProviderType.OPENAI);
        openai.setBaseUrl("https://legacy.example.invalid");
        properties.getProviders().put("openai", openai);

        AiInfoController controller = new AiInfoController(
                properties,
                new AiWebChatProperties(),
                new MockEnvironment(),
                null);

        ApiResponse<AiInfoController.AiInfoResponse> body = controller.providers().getBody();

        assertThat(body.getData().providers()).hasSize(1);
        assertThat(body.getData().providers().get(0).baseUrl()).isNull();
    }

    @Test
    void exposesTeiProviderFromStudioProviderProperties() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultChatProvider("google-ai-gemini");
        properties.setDefaultEmbeddingProvider("kure");

        AiAdapterProperties.Provider tei = new AiAdapterProperties.Provider();
        tei.setType(AiAdapterProperties.ProviderType.TEI);
        tei.setBaseUrl("http://localhost:18080");
        tei.getEmbedding().setEnabled(true);
        tei.getEmbedding().setModel("nlpai-lab/KURE-v1");
        properties.getProviders().put("kure", tei);

        AiInfoController controller = new AiInfoController(
                properties,
                new AiWebChatProperties(),
                new MockEnvironment(),
                null);

        ApiResponse<AiInfoController.AiInfoResponse> body = controller.providers().getBody();

        assertThat(body.getData().defaultEmbeddingProvider()).isEqualTo("kure");
        AiInfoController.ProviderInfo teiInfo = body.getData().providers().get(0);
        assertThat(teiInfo.type()).isEqualTo(AiAdapterProperties.ProviderType.TEI);
        assertThat(teiInfo.baseUrl()).isEqualTo("http://localhost:18080");
        assertThat(teiInfo.chat().model()).isNull();
        assertThat(teiInfo.embedding().model()).isEqualTo("nlpai-lab/KURE-v1");
    }

    @Test
    void skipsProvidersWithoutType() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("google-ai-gemini");

        AiAdapterProperties.Provider incomplete = new AiAdapterProperties.Provider();
        properties.getProviders().put("incomplete", incomplete);

        AiAdapterProperties.Provider google = new AiAdapterProperties.Provider();
        google.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        properties.getProviders().put("google-ai-gemini", google);

        AiInfoController controller = new AiInfoController(
                properties,
                new AiWebChatProperties(),
                new MockEnvironment(),
                null);

        ApiResponse<AiInfoController.AiInfoResponse> body = controller.providers().getBody();

        assertThat(body.getData().providers())
                .extracting(AiInfoController.ProviderInfo::name)
                .containsExactly("google-ai-gemini");
    }

    @Test
    void exposesSplitDefaultProvidersWhenConfigured() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultChatProvider("google-chat");
        properties.setDefaultEmbeddingProvider("google-embedding");

        AiInfoController controller = new AiInfoController(
                properties,
                new AiWebChatProperties(),
                new MockEnvironment(),
                null);

        ApiResponse<AiInfoController.AiInfoResponse> body = controller.providers().getBody();

        assertThat(body.getData().defaultProvider()).isEqualTo("google-chat");
        assertThat(body.getData().defaultChatProvider()).isEqualTo("google-chat");
        assertThat(body.getData().defaultEmbeddingProvider()).isEqualTo("google-embedding");
    }
}
