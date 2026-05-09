package studio.one.platform.autoconfigure.features.condition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import studio.one.platform.autoconfigure.PersistenceProperties;

class OnFeaturePersistenceConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void myBatisAsJdbcLeavesMigratedFeaturesOnMyBatisBranch() {
        contextRunner
                .withPropertyValues("studio.persistence.type=mybatis")
                .run(context -> assertThat(context).hasBean("migratedMyBatis"));
    }

    @Test
    void myBatisAsJdbcMapsLegacyFeatureToJdbcBranch() {
        contextRunner
                .withPropertyValues("studio.persistence.type=mybatis")
                .run(context -> {
                    assertThat(context).hasBean("legacyJdbc");
                    assertThat(context).doesNotHaveBean("legacyMyBatis");
                });
    }

    @Test
    void explicitMyBatisCanAlsoMapToLegacyJdbcBranch() {
        contextRunner
                .withPropertyValues(
                        "studio.persistence.type=jpa",
                        "studio.features.legacy.persistence=mybatis")
                .run(context -> assertThat(context).hasBean("legacyJdbc"));
    }

    @Test
    void invalidFeaturePersistenceFailsFast() {
        contextRunner
                .withPropertyValues("studio.features.legacy.persistence=mybtais")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("studio.features.legacy.persistence")
                        .hasStackTraceContaining("jpa, mybatis, jdbc"));
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        @ConditionalOnFeaturePersistence(feature = "migrated", value = PersistenceProperties.Type.mybatis)
        String migratedMyBatis() {
            return "migratedMyBatis";
        }

        @Bean
        @ConditionalOnFeaturePersistence(feature = "legacy", value = PersistenceProperties.Type.jdbc,
                mybatisAsJdbc = true)
        String legacyJdbc() {
            return "legacyJdbc";
        }

        @Bean
        @ConditionalOnFeaturePersistence(feature = "legacy", value = PersistenceProperties.Type.mybatis,
                mybatisAsJdbc = true)
        String legacyMyBatis() {
            return "legacyMyBatis";
        }
    }
}
