package studio.one.platform.ai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.core.vector.visualization.ExistingVectorItemRepository;
import studio.one.platform.ai.core.vector.visualization.ProjectionAlgorithm;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPoint;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPointRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionRepository;
import studio.one.platform.ai.service.visualization.DefaultVectorProjectionJobService;
import studio.one.platform.ai.service.visualization.DefaultVectorProjectionService;
import studio.one.platform.ai.service.visualization.VectorProjectionJobService;
import studio.one.platform.ai.service.visualization.VectorProjectionService;
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
            assertThat(context.getBean(VectorProjectionGenerator.class).algorithm()).isEqualTo(ProjectionAlgorithm.PCA);
        });
    }

    @Test
    void customGeneratorIsIncludedInAutoConfiguredJobServiceWithDefaults() {
        VectorProjectionGenerator customPca = new VectorProjectionGenerator() {
            @Override
            public ProjectionAlgorithm algorithm() {
                return ProjectionAlgorithm.PCA;
            }

            @Override
            public java.util.List<VectorProjectionPoint> generate(
                    String projectionId,
                    java.util.List<VectorItem> items,
                    java.time.Instant createdAt) {
                return java.util.List.of();
            }
        };

        contextRunner
                .withBean(VectorProjectionGenerator.class, () -> customPca)
                .withBean(VectorProjectionRepository.class, () -> mock(VectorProjectionRepository.class))
                .withBean(VectorProjectionPointRepository.class, () -> mock(VectorProjectionPointRepository.class))
                .withBean(ExistingVectorItemRepository.class, () -> mock(ExistingVectorItemRepository.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(VectorProjectionJobService.class);
                    assertThat(context).hasSingleBean(VectorProjectionService.class);
                    DefaultVectorProjectionJobService jobService =
                            (DefaultVectorProjectionJobService) context.getBean(VectorProjectionJobService.class);
                    @SuppressWarnings("unchecked")
                    java.util.List<VectorProjectionGenerator> generators =
                            (java.util.List<VectorProjectionGenerator>) ReflectionTestUtils.getField(jobService, "generators");
                    assertThat(generators)
                            .contains(customPca)
                            .extracting(VectorProjectionGenerator::algorithm)
                            .contains(ProjectionAlgorithm.PCA, ProjectionAlgorithm.UMAP, ProjectionAlgorithm.TSNE);
                    DefaultVectorProjectionService projectionService =
                            (DefaultVectorProjectionService) context.getBean(VectorProjectionService.class);
                    assertThat(ReflectionTestUtils.getField(projectionService, "jobService")).isSameAs(jobService);
                });
    }
}
