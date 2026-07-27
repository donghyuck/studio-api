package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.model.ModelCatalog;
import studio.one.platform.ai.model.ModelDeploymentRegistry;
import studio.one.platform.ai.model.ModelWorkload;
import studio.one.platform.ai.model.embedding.EmbeddingSpaceContract;

class ModelDeploymentRegistryConfigurationTest {

    private final ModelDeploymentRegistryConfiguration configuration =
            new ModelDeploymentRegistryConfiguration();
    private final ModelCatalog catalog = configuration.builtInModelCatalog();
    private final ChatPort chatPort = request -> null;
    private final EmbeddingPort embeddingPort = request -> null;

    @Test
    void configuredDeploymentsWinAndResolveProviderPorts() {
        AiAdapterProperties adapters = adapters();
        ModelDeploymentProperties properties = new ModelDeploymentProperties();
        properties.getModelDeployments().put("chat-default",
                deployment("google-chat", "google/gemini-2.5-flash", ModelWorkload.CHAT, null));
        properties.getModelDeployments().put("humanities-text-v1",
                deployment("google-embedding", "google/gemini-embedding-001", ModelWorkload.EMBEDDING, 768));
        properties.getRouting().setDefaultChatDeployment("chat-default");
        properties.getRouting().setDefaultEmbeddingDeployment("humanities-text-v1");

        ModelDeploymentRegistry registry = configuration.modelDeploymentRegistry(
                catalog, properties, adapters, new MockEnvironment(),
                Map.of("google-chat", chatPort), Map.of("google-embedding", embeddingPort));

        assertThat(registry.defaultDeployment(ModelWorkload.CHAT)).get()
                .extracting(deployment -> deployment.deploymentId())
                .isEqualTo("chat-default");
        assertThat(registry.chatPort(null)).isSameAs(chatPort);
        assertThat(registry.embeddingPort("humanities-text-v1")).isSameAs(embeddingPort);
    }

    @Test
    void synthesizesLegacyDeploymentsWhenNewConfigurationIsAbsent() {
        AiAdapterProperties adapters = adapters();
        adapters.getRouting().setDefaultChatProvider("google-chat");
        adapters.getRouting().setDefaultEmbeddingProvider("google-embedding");

        ModelDeploymentRegistry registry = configuration.modelDeploymentRegistry(
                catalog, new ModelDeploymentProperties(), adapters, new MockEnvironment(),
                Map.of("google-chat", chatPort), Map.of("google-embedding", embeddingPort));

        assertThat(registry.find("google-chat-chat")).isPresent();
        assertThat(registry.find("google-embedding-embedding")).isPresent();
        assertThat(registry.defaultDeployment(ModelWorkload.EMBEDDING)).get()
                .extracting(deployment -> deployment.definition().apiModel())
                .isEqualTo("gemini-embedding-001");
    }

    @Test
    void rejectsModelThatDoesNotMatchFixedProviderChannel() {
        AiAdapterProperties adapters = adapters();
        ModelDeploymentProperties properties = new ModelDeploymentProperties();
        properties.getModelDeployments().put("chat-default",
                deployment("google-chat", "google/gemini-2.5-pro", ModelWorkload.CHAT, null));
        properties.getRouting().setDefaultChatDeployment("chat-default");

        assertThatThrownBy(() -> configuration.modelDeploymentRegistry(
                catalog, properties, adapters, new MockEnvironment(),
                Map.of("google-chat", chatPort), Map.of("google-embedding", embeddingPort)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider channel uses gemini-2.5-flash");
    }

    @Test
    void createsDistinctDeploymentPortsFromOneConnectionProvider() {
        AiAdapterProperties adapters = new AiAdapterProperties();
        AiAdapterProperties.Provider google = new AiAdapterProperties.Provider();
        google.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        google.getChat().setEnabled(true);
        google.getEmbedding().setEnabled(true);
        adapters.getProviders().put("google-ai", google);

        ModelDeploymentProperties properties = new ModelDeploymentProperties();
        properties.getModelDeployments().put("chat-default",
                deployment("google-ai", "google/gemini-2.5-flash", ModelWorkload.CHAT, null));
        properties.getModelDeployments().put("chat-pro",
                deployment("google-ai", "google/gemini-2.5-pro", ModelWorkload.CHAT, null));
        ModelDeploymentProperties.Deployment textEmbedding =
                deployment("google-ai", "google/gemini-embedding-001", ModelWorkload.EMBEDDING, null);
        textEmbedding.setIndexTaskType("retrieval_document");
        textEmbedding.setQueryTaskType("retrieval_query");
        properties.getModelDeployments().put("text-embedding", textEmbedding);
        properties.getModelDeployments().put("multimodal-embedding",
                deployment("google-ai", "google/gemini-embedding-2", ModelWorkload.EMBEDDING, null));
        properties.getRouting().setDefaultChatDeployment("chat-default");
        properties.getRouting().setDefaultEmbeddingDeployment("text-embedding");

        List<String> chatModels = new ArrayList<>();
        List<String> embeddingModels = new ArrayList<>();
        List<String> embeddingContracts = new ArrayList<>();
        ProviderChatPortFactory chatFactory = deploymentAwareChatFactory(chatModels);
        ProviderEmbeddingPortFactory embeddingFactory =
                deploymentAwareEmbeddingFactory(embeddingModels, embeddingContracts);

        ModelDeploymentRegistry registry = configuration.modelDeploymentRegistry(
                catalog, properties, adapters, new MockEnvironment(), Map.of(), Map.of(),
                List.of(chatFactory), List.of(embeddingFactory),
                emptyProvider(org.springframework.ai.chat.model.ChatModel.class),
                emptyProvider(org.springframework.ai.embedding.EmbeddingModel.class));

        assertThat(chatModels).containsExactly("gemini-2.5-flash", "gemini-2.5-pro");
        assertThat(embeddingModels).containsExactly(
                "gemini-embedding-001@768", "gemini-embedding-2@768");
        assertThat(embeddingContracts).containsExactly(
                "retrieval_document/retrieval_query",
                "provider-default/provider-default");
        assertThat(registry.chatPort("chat-default")).isNotSameAs(registry.chatPort("chat-pro"));
        assertThat(registry.embeddingPort("text-embedding"))
                .isNotSameAs(registry.embeddingPort("multimodal-embedding"));
    }

    private ProviderChatPortFactory deploymentAwareChatFactory(List<String> models) {
        return new ProviderChatPortFactory() {
            @Override
            public AiAdapterProperties.ProviderType supportedType() {
                return AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI;
            }

            @Override
            public ChatPort create(AiAdapterProperties.Provider provider,
                    org.springframework.core.env.Environment environment,
                    ObjectProvider<org.springframework.ai.chat.model.ChatModel> modelProvider) {
                return request -> null;
            }

            @Override
            public ChatPort createForDeployment(String providerId, AiAdapterProperties.Provider provider,
                    String apiModel, org.springframework.core.env.Environment environment,
                    ObjectProvider<org.springframework.ai.chat.model.ChatModel> modelProvider) {
                models.add(apiModel);
                return new ChatPort() {
                    @Override
                    public studio.one.platform.ai.core.chat.ChatResponse chat(
                            studio.one.platform.ai.core.chat.ChatRequest request) {
                        return null;
                    }
                };
            }
        };
    }

    private ProviderEmbeddingPortFactory deploymentAwareEmbeddingFactory(
            List<String> models,
            List<String> contracts) {
        return new ProviderEmbeddingPortFactory() {
            @Override
            public AiAdapterProperties.ProviderType supportedType() {
                return AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI;
            }

            @Override
            public EmbeddingPort create(AiAdapterProperties.Provider provider,
                    org.springframework.core.env.Environment environment,
                    ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> modelProvider) {
                return request -> null;
            }

            @Override
            public EmbeddingPort createForDeployment(String providerId, AiAdapterProperties.Provider provider,
                    String apiModel, Integer dimension, org.springframework.core.env.Environment environment,
                    ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> modelProvider) {
                models.add(apiModel + "@" + dimension);
                return new EmbeddingPort() {
                    @Override
                    public studio.one.platform.ai.core.embedding.EmbeddingResponse embed(
                            studio.one.platform.ai.core.embedding.EmbeddingRequest request) {
                        return null;
                    }
                };
            }

            @Override
            public EmbeddingPort createForDeployment(String providerId, AiAdapterProperties.Provider provider,
                    String apiModel, Integer dimension, EmbeddingSpaceContract embeddingContract,
                    org.springframework.core.env.Environment environment,
                    ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> modelProvider) {
                contracts.add(embeddingContract.indexTaskType() + "/" + embeddingContract.queryTaskType());
                return createForDeployment(
                        providerId, provider, apiModel, dimension, environment, modelProvider);
            }
        };
    }

    private <T> ObjectProvider<T> emptyProvider(Class<T> type) {
        return new StaticListableBeanFactory().getBeanProvider(type);
    }

    private AiAdapterProperties adapters() {
        AiAdapterProperties properties = new AiAdapterProperties();
        AiAdapterProperties.Provider chat = new AiAdapterProperties.Provider();
        chat.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        chat.getChat().setEnabled(true);
        chat.getChat().setModel("gemini-2.5-flash");
        properties.getProviders().put("google-chat", chat);

        AiAdapterProperties.Provider embedding = new AiAdapterProperties.Provider();
        embedding.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        embedding.getEmbedding().setEnabled(true);
        embedding.getEmbedding().setModel("gemini-embedding-001");
        embedding.getEmbedding().setDimension(768);
        properties.getProviders().put("google-embedding", embedding);
        return properties;
    }

    private ModelDeploymentProperties.Deployment deployment(
            String providerRef, String modelRef, ModelWorkload workload, Integer dimension) {
        ModelDeploymentProperties.Deployment deployment = new ModelDeploymentProperties.Deployment();
        deployment.setProviderRef(providerRef);
        deployment.setModelRef(modelRef);
        deployment.setWorkload(workload);
        deployment.setDimension(dimension);
        return deployment;
    }
}
