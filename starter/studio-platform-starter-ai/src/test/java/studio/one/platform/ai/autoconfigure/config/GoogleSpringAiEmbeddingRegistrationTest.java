package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.autoconfigure.adapter.SpringAiEmbeddingAdapter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;

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
                .withProperty("spring.ai.google.genai.embedding.text.options.model", "text-embedding-004");

        Map<String, EmbeddingPort> embeddingPorts = new ProviderEmbeddingConfiguration().embeddingPorts(
                properties,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class),
                List.of(new GoogleGenAiEmbeddingPortFactoryConfiguration().googleGenAiEmbeddingPortFactory()));

        assertThat(embeddingPorts).containsOnlyKeys("google");
        assertThat(embeddingPorts.get("google")).isInstanceOf(SpringAiEmbeddingAdapter.class);

        AiProviderRegistry registry = new AiProviderRegistry("google", Map.of(), embeddingPorts);
        assertThat(registry.defaultProvider()).isEqualTo("google");
        assertThat(registry.embeddingPort(null)).isSameAs(embeddingPorts.get("google"));
    }

    @Test
    void preservesGoogleEmbeddingTaskTypeInSpringAiOptions() throws Exception {
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
                .withProperty("spring.ai.google.genai.embedding.text.options.model", "text-embedding-004");

        EmbeddingPort port = new ProviderEmbeddingConfiguration().embeddingPorts(
                properties,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class),
                List.of(new GoogleGenAiEmbeddingPortFactoryConfiguration().googleGenAiEmbeddingPortFactory()))
                .get("google");

        assertThat(port).isInstanceOf(SpringAiEmbeddingAdapter.class);

        java.lang.reflect.Field modelField = SpringAiEmbeddingAdapter.class.getDeclaredField("embeddingModel");
        modelField.setAccessible(true);
        Object model = modelField.get(port);

        java.lang.reflect.Field optionsField = model.getClass().getDeclaredField("defaultOptions");
        optionsField.setAccessible(true);
        Object options = optionsField.get(model);

        java.lang.reflect.Method taskTypeMethod = options.getClass().getMethod("getTaskType");
        Object taskType = taskTypeMethod.invoke(options);

        assertThat(String.valueOf(taskType)).isEqualTo("RETRIEVAL_QUERY");
    }
}
