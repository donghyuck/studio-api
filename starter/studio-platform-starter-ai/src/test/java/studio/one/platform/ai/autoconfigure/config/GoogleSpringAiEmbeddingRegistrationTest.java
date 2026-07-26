package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.autoconfigure.adapter.GoogleGenAiEmbeddingAdapter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.model.embedding.EmbeddingSpaceContract;

class GoogleSpringAiEmbeddingRegistrationTest {

    @Test
    void registersGoogleEmbeddingAsSpringAiBackedPath() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("google");

        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.getEmbedding().setEnabled(true);
        provider.getEmbedding().setModel("legacy-should-not-be-used");
        properties.getProviders().put("google", provider);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.google.genai.embedding.api-key", "test-key")
                .withProperty("spring.ai.google.genai.embedding.text.options.model", "gemini-embedding-001")
                .withProperty("spring.ai.google.genai.embedding.text.options.dimensions", "768");

        Map<String, EmbeddingPort> embeddingPorts = new ProviderEmbeddingConfiguration().embeddingPorts(
                properties,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class),
                List.of(new GoogleGenAiEmbeddingPortFactoryConfiguration().googleGenAiEmbeddingPortFactory()));

        assertThat(embeddingPorts).containsOnlyKeys("google");
        assertThat(embeddingPorts.get("google")).isInstanceOf(GoogleGenAiEmbeddingAdapter.class);

        AiProviderRegistry registry = new AiProviderRegistry("google", Map.of(), embeddingPorts);
        assertThat(registry.defaultProvider()).isEqualTo("google");
        assertThat(registry.embeddingPort(null)).isSameAs(embeddingPorts.get("google"));
    }

    @Test
    void ignoresGenericInjectedEmbeddingModelAndUsesGoogleSpringAiProperties() throws Exception {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("google");

        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.getEmbedding().setEnabled(true);
        properties.getProviders().put("google", provider);

        org.springframework.ai.embedding.EmbeddingModel injected =
                org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("googleEmbeddingModel", injected);

        EmbeddingPort port = new ProviderEmbeddingConfiguration().embeddingPorts(
                properties,
                new MockEnvironment()
                        .withProperty("spring.ai.google.genai.embedding.api-key", "spring-key")
                        .withProperty("spring.ai.google.genai.embedding.text.options.model", "gemini-embedding-001"),
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class),
                List.of(new GoogleGenAiEmbeddingPortFactoryConfiguration().googleGenAiEmbeddingPortFactory()))
                .get("google");

        assertThat(port).isInstanceOf(GoogleGenAiEmbeddingAdapter.class);
    }

    @Test
    void preservesLegacyGoogleEmbeddingTaskTypeAsAdapterDefault() throws Exception {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("google");

        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.getEmbedding().setEnabled(true);
        provider.getGoogleEmbedding().setTaskType("RETRIEVAL_QUERY");
        properties.getProviders().put("google", provider);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.google.genai.embedding.api-key", "test-key")
                .withProperty("spring.ai.google.genai.embedding.text.options.model", "gemini-embedding-001")
                .withProperty("spring.ai.google.genai.embedding.text.options.dimensions", "768");

        EmbeddingPort port = new ProviderEmbeddingConfiguration().embeddingPorts(
                properties,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class),
                List.of(new GoogleGenAiEmbeddingPortFactoryConfiguration().googleGenAiEmbeddingPortFactory()))
                .get("google");

        assertThat(port).isInstanceOf(GoogleGenAiEmbeddingAdapter.class);
        assertThat(field(port, "defaultTaskType")).isEqualTo("RETRIEVAL_QUERY");
        assertThat(field(port, "configuredDimension")).isEqualTo(768);
    }

    @Test
    void explicitModelOverrideCreatesAProviderWithItsOwnEmbeddingModel() throws Exception {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("google-embedding-2");

        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.getEmbedding().setEnabled(true);
        provider.getEmbedding().setModel("gemini-embedding-2");
        provider.getEmbedding().setModelOverride(true);
        provider.getEmbedding().setDimension(768);
        properties.getProviders().put("google-embedding-2", provider);

        EmbeddingPort port = new ProviderEmbeddingConfiguration().embeddingPorts(
                properties,
                new MockEnvironment()
                        .withProperty("spring.ai.google.genai.embedding.api-key", "spring-key")
                        .withProperty("spring.ai.google.genai.embedding.text.options.model", "gemini-embedding-001")
                        .withProperty("spring.ai.google.genai.embedding.text.options.dimensions", "3072"),
                new StaticListableBeanFactory().getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class),
                List.of(new GoogleGenAiEmbeddingPortFactoryConfiguration().googleGenAiEmbeddingPortFactory()))
                .get("google-embedding-2");

        assertThat(port).isInstanceOf(GoogleGenAiEmbeddingAdapter.class);
        assertThat(field(port, "configuredModel")).isEqualTo("gemini-embedding-2");
        assertThat(field(port, "configuredDimension")).isEqualTo(768);
        assertThat(field(port, "taskTypeSupported")).isEqualTo(false);
    }

    @Test
    void preservesDeploymentIndexAndQueryTaskContract() throws Exception {
        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.getEmbedding().setEnabled(true);
        EmbeddingSpaceContract contract = new EmbeddingSpaceContract(
                "v1", "google", "gemini-embedding-001", 768,
                "provider-default", "retrieval_document", "retrieval_query",
                "text", "1", Map.of());

        EmbeddingPort port = new GoogleGenAiEmbeddingPortFactoryConfiguration.GoogleGenAiEmbeddingPortFactory()
                .createForDeployment(
                        "google",
                        provider,
                        "gemini-embedding-001",
                        768,
                        contract,
                        new MockEnvironment()
                                .withProperty("spring.ai.google.genai.embedding.api-key", "spring-key"),
                        new StaticListableBeanFactory()
                                .getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class));

        assertThat(field(port, "indexTaskType")).isEqualTo("RETRIEVAL_DOCUMENT");
        assertThat(field(port, "queryTaskType")).isEqualTo("RETRIEVAL_QUERY");
    }

    @Test
    void rejectsExplicitTaskContractForGeminiEmbedding2() {
        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.getEmbedding().setEnabled(true);
        EmbeddingSpaceContract contract = new EmbeddingSpaceContract(
                "v1", "google", "gemini-embedding-2", 768,
                "provider-default", "retrieval_document", "retrieval_query",
                "text", "1", Map.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new GoogleGenAiEmbeddingPortFactoryConfiguration.GoogleGenAiEmbeddingPortFactory()
                                .createForDeployment(
                                        "google",
                                        provider,
                                        "gemini-embedding-2",
                                        768,
                                        contract,
                                        new MockEnvironment().withProperty(
                                                "spring.ai.google.genai.embedding.api-key", "spring-key"),
                                        new StaticListableBeanFactory().getBeanProvider(
                                                org.springframework.ai.embedding.EmbeddingModel.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not support");
    }

    private static Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
