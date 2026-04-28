package studio.one.application.mail.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Set;

import jakarta.validation.ConstraintViolationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import studio.one.application.mail.config.ImapProperties;
import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;

@ExtendWith(OutputCaptureExtension.class)
class MailImapPropertiesConfigurationTest {

    private final ApplicationContextRunner propertiesRunner = new ApplicationContextRunner()
            .withUserConfiguration(MailImapPropertiesConfiguration.class);

    private final ApplicationContextRunner guardRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSecretPresenceGuard.class));

    @BeforeEach
    void resetWarnings() throws Exception {
        Field warned = ConfigurationPropertyMigration.class.getDeclaredField("WARNED");
        warned.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> values = (Set<String>) warned.get(null);
        values.clear();
    }

    @Test
    void bindsTargetImapProperties(CapturedOutput output) {
        propertiesRunner
                .withPropertyValues(
                        "studio.mail.imap.host=target.example.com",
                        "studio.mail.imap.username=target-user",
                        "studio.mail.imap.password=target-secret",
                        "studio.mail.imap.port=1993")
                .run(context -> {
                    ImapProperties properties = context.getBean(ImapProperties.class);

                    assertThat(properties.getHost()).isEqualTo("target.example.com");
                    assertThat(properties.getUsername()).isEqualTo("target-user");
                    assertThat(properties.getPassword()).isEqualTo("target-secret");
                    assertThat(properties.getPort()).isEqualTo(1993);
                    assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
                });
    }

    @Test
    void bindsLegacyImapPropertiesWhenTargetMissing(CapturedOutput output) {
        propertiesRunner
                .withPropertyValues(
                        "studio.features.mail.imap.host=legacy.example.com",
                        "studio.features.mail.imap.username=legacy-user",
                        "studio.features.mail.imap.password=legacy-secret")
                .run(context -> {
                    ImapProperties properties = context.getBean(ImapProperties.class);

                    assertThat(properties.getHost()).isEqualTo("legacy.example.com");
                    assertThat(properties.getUsername()).isEqualTo("legacy-user");
                    assertThat(properties.getPassword()).isEqualTo("legacy-secret");
                    assertThat(output)
                            .contains("[DEPRECATED CONFIG] studio.features.mail.imap.host is deprecated")
                            .contains("Use studio.mail.imap.host instead");
                });
    }

    @Test
    void targetImapPropertiesTakePriorityOverLegacy(CapturedOutput output) {
        propertiesRunner
                .withPropertyValues(
                        "studio.mail.imap.host=target.example.com",
                        "studio.mail.imap.username=target-user",
                        "studio.mail.imap.password=target-secret",
                        "studio.features.mail.imap.host=legacy.example.com",
                        "studio.features.mail.imap.username=legacy-user",
                        "studio.features.mail.imap.password=legacy-secret")
                .run(context -> {
                    ImapProperties properties = context.getBean(ImapProperties.class);

                    assertThat(properties.getHost()).isEqualTo("target.example.com");
                    assertThat(properties.getUsername()).isEqualTo("target-user");
                    assertThat(properties.getPassword()).isEqualTo("target-secret");
                    assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
                });
    }

    @Test
    void targetLeavesWinAndMissingRequiredLeavesFallbackToLegacy(CapturedOutput output) {
        propertiesRunner
                .withPropertyValues(
                        "studio.mail.imap.host=target.example.com",
                        "studio.features.mail.imap.host=legacy.example.com",
                        "studio.features.mail.imap.username=legacy-user",
                        "studio.features.mail.imap.password=legacy-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ImapProperties properties = context.getBean(ImapProperties.class);

                    assertThat(properties.getHost()).isEqualTo("target.example.com");
                    assertThat(properties.getUsername()).isEqualTo("legacy-user");
                    assertThat(properties.getPassword()).isEqualTo("legacy-secret");
                    assertThat(output)
                            .contains("[DEPRECATED CONFIG] studio.features.mail.imap.username is deprecated")
                            .contains("[DEPRECATED CONFIG] studio.features.mail.imap.password is deprecated")
                            .doesNotContain("[DEPRECATED CONFIG] studio.features.mail.imap.host is deprecated");
                });
    }

    @Test
    void invalidTargetImapPropertiesFailFast() {
        propertiesRunner
                .withPropertyValues(
                        "studio.mail.imap.host=target.example.com",
                        "studio.mail.imap.username=target-user",
                        "studio.mail.imap.password=target-secret",
                        "studio.mail.imap.port=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(ConstraintViolationException.class);
                });
    }

    @Test
    void invalidLegacyImapPropertiesFailFast() {
        propertiesRunner
                .withPropertyValues(
                        "studio.features.mail.imap.host=legacy.example.com",
                        "studio.features.mail.imap.username=legacy-user",
                        "studio.features.mail.imap.password=legacy-secret",
                        "studio.features.mail.imap.concurrency=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(ConstraintViolationException.class);
                });
    }

    @Test
    void featureDisabledSkipsImapGuardAndMigration(CapturedOutput output) {
        guardRunner
                .withPropertyValues(
                        "studio.features.mail.enabled=false",
                        "studio.features.mail.imap.host=legacy.example.com",
                        "studio.features.mail.imap.username=legacy-user",
                        "studio.features.mail.imap.password=legacy-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MailSecretPresenceGuard.class);
                    assertThat(context).doesNotHaveBean(ImapProperties.class);
                    assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
                });
    }
}
