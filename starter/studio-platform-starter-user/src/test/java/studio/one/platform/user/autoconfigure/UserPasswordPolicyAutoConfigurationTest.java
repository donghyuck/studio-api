package studio.one.platform.user.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import studio.one.base.user.config.PasswordPolicyProperties;

@ExtendWith(OutputCaptureExtension.class)
class UserPasswordPolicyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UserPasswordPolicyAutoConfiguration.class));

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
                .contains("[DEPRECATED CONFIG] studio.features.user.password-policy.* is deprecated")
                .contains("Use studio.user.password-policy.* instead");
        assertThat(countOccurrences(output.toString(), "[DEPRECATED CONFIG] studio.features.user.password-policy.*"))
                .isEqualTo(1);
    }

    @Test
    void targetPrefixWinsWhenTargetAndLegacyArePresent(CapturedOutput output) {
        contextRunner.withPropertyValues(
                "studio.user.password-policy.min-length=16",
                "studio.user.password-policy.require-lower=true",
                "studio.features.user.password-policy.min-length=10",
                "studio.features.user.password-policy.max-length=80",
                "studio.features.user.password-policy.require-lower=false")
                .run(context -> {
                    PasswordPolicyProperties properties = context.getBean(PasswordPolicyProperties.class);

                    assertThat(properties.getMinLength()).isEqualTo(16);
                    assertThat(properties.getMaxLength()).isEqualTo(20);
                    assertThat(properties.isRequireLower()).isTrue();
                });

        assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
    }

    private static int countOccurrences(String text, String value) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }
}
