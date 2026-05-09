package studio.one.platform.security.autoconfigure.condition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import studio.one.platform.autoconfigure.PersistenceProperties;

class SecurityPersistenceConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SecurityPersistenceConfig.class);

    @Test
    void globalMyBatisFallsBackToDirectJdbcBranches() {
        contextRunner
                .withPropertyValues("studio.persistence.type=mybatis")
                .run(context -> {
                    assertThat(context).hasBean("jwtJdbc");
                    assertThat(context).hasBean("accountLockJdbc");
                    assertThat(context).hasBean("loginFailureJdbc");
                    assertThat(context).hasBean("passwordResetJdbc");
                    assertThat(context).doesNotHaveBean("jwtJpa");
                });
    }

    @Test
    void explicitMyBatisOverrideFallsBackToDirectJdbcBranches() {
        contextRunner
                .withPropertyValues(
                        "studio.persistence.type=jpa",
                        "studio.security.jwt.persistence=mybatis",
                        "studio.security.auth.lock.persistence=mybatis",
                        "studio.security.audit.login-failure.persistence=mybatis",
                        "studio.security.auth.password-reset.persistence=mybatis")
                .run(context -> {
                    assertThat(context).hasBean("jwtJdbc");
                    assertThat(context).hasBean("accountLockJdbc");
                    assertThat(context).hasBean("loginFailureJdbc");
                    assertThat(context).hasBean("passwordResetJdbc");
                    assertThat(context).doesNotHaveBean("jwtJpa");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class SecurityPersistenceConfig {

        @Bean
        @ConditionalOnJwtPersistence(PersistenceProperties.Type.jpa)
        String jwtJpa() {
            return "jwtJpa";
        }

        @Bean
        @ConditionalOnJwtPersistence(PersistenceProperties.Type.jdbc)
        String jwtJdbc() {
            return "jwtJdbc";
        }

        @Bean
        @ConditionalOnAccountLockPersistence(PersistenceProperties.Type.jdbc)
        String accountLockJdbc() {
            return "accountLockJdbc";
        }

        @Bean
        @ConditionalOnLoginFailurePersistence(PersistenceProperties.Type.jdbc)
        String loginFailureJdbc() {
            return "loginFailureJdbc";
        }

        @Bean
        @ConditionalOnPasswordResetPersistence(PersistenceProperties.Type.jdbc)
        String passwordResetJdbc() {
            return "passwordResetJdbc";
        }
    }
}
