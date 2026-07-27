package studio.one.platform.autoconfigure.jasypt;

import static org.assertj.core.api.Assertions.assertThat;

import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JasyptPropertiesTest {

    private static final String VALID_TEST_PASSWORD = String.join("-",
            "not", "a", "real", "test", "password", "123456");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ValidationAutoConfiguration.class,
                    JasyptAutoConfiguration.class));

    @Test
    void rejectsShortEncryptorPasswordAtBindingTime() {
        contextRunner
                .withPropertyValues(
                        "studio.features.jasypt.enabled=true",
                        "studio.features.jasypt.encryptor.password=short")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("Could not bind properties to 'JasyptProperties'")
                            .rootCause()
                            .hasMessageContaining("password")
                            .hasMessageContaining("16");
                });
    }

    @Test
    void createsEncryptorForValidPassword() {
        contextRunner
                .withPropertyValues(
                        "studio.features.jasypt.enabled=true",
                        "studio.features.jasypt.encryptor.password=" + VALID_TEST_PASSWORD)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JasyptProperties.class);
                    assertThat(context).hasSingleBean(StringEncryptor.class);
                });
    }
}
