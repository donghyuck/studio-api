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

class OllamaSpringAiEmbeddingRegistrationTest {

    @Test
    void registersOllamaEmbeddingAsSpringAiBackedPath() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("ollama");

        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.OLLAMA);
        provider.getEmbedding().setEnabled(true);
        provider.getEmbedding().setModel("legacy-should-not-be-used");
        properties.getProviders().put("ollama", provider);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.ollama.base-url", "http://localhost:11434")
                .withProperty("spring.ai.ollama.embedding.options.model", "nomic-embed-text");

        Map<String, EmbeddingPort> embeddingPorts = new ProviderEmbeddingConfiguration().embeddingPorts(
                properties,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class),
                List.of(new OllamaPortFactoryConfiguration().ollamaEmbeddingPortFactory()));

        assertThat(embeddingPorts).containsOnlyKeys("ollama");
        assertThat(embeddingPorts.get("ollama")).isInstanceOf(SpringAiEmbeddingAdapter.class);

        AiProviderRegistry registry = new AiProviderRegistry("ollama", Map.of(), embeddingPorts);
        assertThat(registry.defaultProvider()).isEqualTo("ollama");
        assertThat(registry.embeddingPort(null)).isSameAs(embeddingPorts.get("ollama"));
    }

    @Test
    void ignoresGenericInjectedEmbeddingModelAndUsesOllamaSpringAiProperties() throws Exception {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("ollama");

        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.OLLAMA);
        provider.getEmbedding().setEnabled(true);
        properties.getProviders().put("ollama", provider);

        org.springframework.ai.embedding.EmbeddingModel injected =
                org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("ollamaEmbeddingModel", injected);

        EmbeddingPort port = new ProviderEmbeddingConfiguration().embeddingPorts(
                properties,
                new MockEnvironment()
                        .withProperty("spring.ai.ollama.embedding.options.model", "nomic-embed-text"),
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class),
                List.of(new OllamaPortFactoryConfiguration().ollamaEmbeddingPortFactory()))
                .get("ollama");

        java.lang.reflect.Field modelField = SpringAiEmbeddingAdapter.class.getDeclaredField("embeddingModel");
        modelField.setAccessible(true);

        assertThat(modelField.get(port)).isNotSameAs(injected);
    }
}
