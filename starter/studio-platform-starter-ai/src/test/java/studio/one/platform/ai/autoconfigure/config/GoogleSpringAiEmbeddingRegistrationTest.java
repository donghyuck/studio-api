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

        LangChainEmbeddingConfiguration embeddingConfiguration = new LangChainEmbeddingConfiguration();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        ObjectProvider<I18n> i18nProvider = beanFactory.getBeanProvider(I18n.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.google.genai.embedding.api-key", "test-key")
                .withProperty("spring.ai.google.genai.embedding.text.options.model", "text-embedding-004");

        Map<String, EmbeddingPort> embeddingPorts = embeddingConfiguration.embeddingPorts(
                properties,
                i18nProvider,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class));

        assertThat(embeddingPorts).containsOnlyKeys("google");
        assertThat(embeddingPorts.get("google")).isInstanceOf(SpringAiEmbeddingAdapter.class);

        AiProviderRegistry registry = new AiProviderRegistry("google", Map.of(), embeddingPorts);
        assertThat(registry.defaultProvider()).isEqualTo("google");
        assertThat(registry.embeddingPort(null)).isSameAs(embeddingPorts.get("google"));
    }
}
