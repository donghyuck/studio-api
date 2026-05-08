package studio.one.platform.autoconfigure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PersistenceResolverAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PersistenceResolverAutoConfiguration.class));

    @Test
    void registersPersistenceTypeResolver() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PersistenceTypeResolver.class);
        });
    }

    @Test
    void backsOffWhenResolverExists() {
        PersistenceTypeResolver customResolver = (featureName, defaultType) -> StudioPersistenceType.JPA;
        contextRunner
                .withBean(PersistenceTypeResolver.class, () -> customResolver)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PersistenceTypeResolver.class);
                    assertThat(context.getBean(PersistenceTypeResolver.class)).isSameAs(customResolver);
                });
    }
}
