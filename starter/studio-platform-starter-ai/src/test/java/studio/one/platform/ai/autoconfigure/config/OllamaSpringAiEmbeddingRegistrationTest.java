package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.autoconfigure.adapter.SpringAiEmbeddingAdapter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.service.I18n;

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

        ProviderEmbeddingConfiguration embeddingConfiguration = new ProviderEmbeddingConfiguration();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        ObjectProvider<I18n> i18nProvider = beanFactory.getBeanProvider(I18n.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.ollama.base-url", "http://localhost:11434")
                .withProperty("spring.ai.ollama.embedding.options.model", "nomic-embed-text");

        Map<String, EmbeddingPort> embeddingPorts = embeddingConfiguration.embeddingPorts(
                properties,
                i18nProvider,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class));

        assertThat(embeddingPorts).containsOnlyKeys("ollama");
        assertThat(embeddingPorts.get("ollama")).isInstanceOf(SpringAiEmbeddingAdapter.class);

        AiProviderRegistry registry = new AiProviderRegistry("ollama", Map.of(), embeddingPorts);
        assertThat(registry.defaultProvider()).isEqualTo("ollama");
        assertThat(registry.embeddingPort(null)).isSameAs(embeddingPorts.get("ollama"));
    }
}
