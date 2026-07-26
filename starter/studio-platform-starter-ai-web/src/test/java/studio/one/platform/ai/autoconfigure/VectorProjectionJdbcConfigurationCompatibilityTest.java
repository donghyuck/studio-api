package studio.one.platform.ai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.platform.ai.core.vector.visualization.ExistingVectorItemRepository;
import studio.one.platform.ai.service.visualization.JdbcExistingVectorItemRepository;

class VectorProjectionJdbcConfigurationCompatibilityTest {

    @Test
    void skipsJdbcProjectionConfigurationWhenImplementationArtifactIsOlder() {
        new ApplicationContextRunner()
                .withUserConfiguration(AiWebAutoConfiguration.VectorProjectionJdbcConfiguration.class)
                .withClassLoader(new FilteredClassLoader(JdbcExistingVectorItemRepository.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ExistingVectorItemRepository.class);
                });
    }
}
