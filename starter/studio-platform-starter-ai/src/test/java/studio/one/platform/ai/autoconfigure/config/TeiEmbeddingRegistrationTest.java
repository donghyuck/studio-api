package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.autoconfigure.adapter.TeiEmbeddingAdapter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

class TeiEmbeddingRegistrationTest {

    @Test
    void registersTeiEmbeddingProvider() {
        AiAdapterProperties properties = new AiAdapterProperties();

        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.TEI);
        provider.setBaseUrl("http://localhost:8080");
        provider.getEmbedding().setEnabled(true);
        provider.getEmbedding().setModel("nlpai-lab/KURE-v1");
        properties.getProviders().put("kure", provider);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        Map<String, EmbeddingPort> embeddingPorts = new ProviderEmbeddingConfiguration().embeddingPorts(
                properties,
                new MockEnvironment(),
                beanFactory.getBeanProvider(org.springframework.ai.embedding.EmbeddingModel.class),
                List.of(new TeiPortFactoryConfiguration().teiEmbeddingPortFactory()));

        assertThat(embeddingPorts).containsOnlyKeys("kure");
        assertThat(embeddingPorts.get("kure")).isInstanceOf(TeiEmbeddingAdapter.class);
    }
}
