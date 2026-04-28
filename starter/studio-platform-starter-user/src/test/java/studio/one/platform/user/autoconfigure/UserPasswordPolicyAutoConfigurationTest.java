package studio.one.platform.user.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import studio.one.base.user.config.PasswordPolicyProperties;
import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;

@ExtendWith(OutputCaptureExtension.class)
class UserPasswordPolicyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UserPasswordPolicyAutoConfiguration.class));

    @BeforeEach
    void resetWarnings() throws Exception {
        Field warned = ConfigurationPropertyMigration.class.getDeclaredField("WARNED");
        warned.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> values = (Set<String>) warned.get(null);
        values.clear();
    }

    @Test
    void targetOnlyBindsCanonicalPasswordPolicyPrefix(CapturedOutput output) {
        contextRunner.withPropertyValues(
                "studio.user.password-policy.min-length=12",
                "studio.user.password-policy.max-length=64",
                "studio.user.password-policy.require-upper=true")
                .run(context -> {
                    PasswordPolicyProperties properties = context.getBean(PasswordPolicyProperties.class);

                    assertThat(properties.getMinLength()).isEqualTo(12);
                    assertThat(properties.getMaxLength()).isEqualTo(64);
                    assertThat(properties.isRequireUpper()).isTrue();
                });

        assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
    }

    @Test
    void legacyOnlyFallsBackAndWarnsOnce(CapturedOutput output) {
        contextRunner.withPropertyValues(
                "studio.features.user.password-policy.min-length=14",
                "studio.features.user.password-policy.require-digit=true")
                .run(context -> {
                    PasswordPolicyProperties properties = context.getBean(PasswordPolicyProperties.class);

                    assertThat(properties.getMinLength()).isEqualTo(14);
                    assertThat(properties.isRequireDigit()).isTrue();
                });

        contextRunner.withPropertyValues(
                "studio.features.user.password-policy.max-length=48",
                "studio.features.user.password-policy.require-special=true")
                .run(context -> {
                    PasswordPolicyProperties properties = context.getBean(PasswordPolicyProperties.class);

                    assertThat(properties.getMaxLength()).isEqualTo(48);
                    assertThat(properties.isRequireSpecial()).isTrue();
                });

        assertThat(output)
                .contains("[DEPRECATED CONFIG] studio.features.user.password-policy.min-length is deprecated")
                .contains("Use studio.user.password-policy.min-length instead");
    }

    @Test
    void targetLeavesWinAndMissingLeavesFallbackToLegacy(CapturedOutput output) {
        contextRunner.withPropertyValues(
                "studio.user.password-policy.min-length=16",
                "studio.user.password-policy.require-lower=true",
                "studio.features.user.password-policy.min-length=10",
                "studio.features.user.password-policy.max-length=80",
                "studio.features.user.password-policy.require-lower=false")
                .run(context -> {
                    PasswordPolicyProperties properties = context.getBean(PasswordPolicyProperties.class);

                    assertThat(properties.getMinLength()).isEqualTo(16);
                    assertThat(properties.getMaxLength()).isEqualTo(80);
                    assertThat(properties.isRequireLower()).isTrue();
                });

        assertThat(output)
                .contains("[DEPRECATED CONFIG] studio.features.user.password-policy.max-length is deprecated")
                .doesNotContain("[DEPRECATED CONFIG] studio.features.user.password-policy.min-length is deprecated")
                .doesNotContain("[DEPRECATED CONFIG] studio.features.user.password-policy.require-lower is deprecated");
    }

}
