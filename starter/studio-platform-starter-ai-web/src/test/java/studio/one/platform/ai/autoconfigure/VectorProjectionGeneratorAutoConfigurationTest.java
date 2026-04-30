package studio.one.platform.ai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.core.vector.visualization.ProjectionAlgorithm;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionGenerator;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.service.prompt.PromptRenderer;

class VectorProjectionGeneratorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(AiWebAutoConfiguration.class)
            .withBean(AiProviderRegistry.class, () -> mock(AiProviderRegistry.class))
            .withBean(RagPipelineService.class, () -> mock(RagPipelineService.class))
            .withBean(EmbeddingPort.class, () -> mock(EmbeddingPort.class))
            .withBean(ChatPort.class, () -> mock(ChatPort.class))
            .withBean(PromptRenderer.class, () -> mock(PromptRenderer.class))
            .withBean(AiAdapterProperties.class, AiAdapterProperties::new)
            .withPropertyValues(
                    "studio.features.ai.enabled=true",
                    "studio.ai.endpoints.enabled=true");

    @Test
    void registersDefaultProjectionGeneratorsForSupportedAlgorithms() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            Map<String, VectorProjectionGenerator> generators = context.getBeansOfType(VectorProjectionGenerator.class);
            assertThat(generators.values())
                    .extracting(VectorProjectionGenerator::algorithm)
                    .contains(ProjectionAlgorithm.PCA, ProjectionAlgorithm.UMAP, ProjectionAlgorithm.TSNE);
        });
    }
}
