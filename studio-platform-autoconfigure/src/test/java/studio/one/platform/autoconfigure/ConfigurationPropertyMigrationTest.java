package studio.one.platform.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(OutputCaptureExtension.class)
class ConfigurationPropertyMigrationTest {

    @Test
    void bindLegacyFallbackIfTargetMissingKeepsTargetValuesWhenTargetExists(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("studio.example.value", "target")
                .withProperty("studio.features.example.value", "legacy");
        ExampleProperties properties = new ExampleProperties();
        properties.setValue("target");

        ConfigurationPropertyMigration.bindLegacyFallbackIfTargetMissing(
                environment,
                "studio.example",
                "studio.features.example",
                properties,
                LoggerFactory.getLogger(getClass()),
                "Example migration.");

        assertThat(properties.getValue()).isEqualTo("target");
        assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
    }

    @Test
    void bindLegacyFallbackIfTargetMissingBindsLegacyAndWarns(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("studio.features.example.value", "legacy");
        ExampleProperties properties = new ExampleProperties();

        ConfigurationPropertyMigration.bindLegacyFallbackIfTargetMissing(
                environment,
                "studio.example",
                "studio.features.example",
                properties,
                LoggerFactory.getLogger(getClass()),
                "Example migration.");

        assertThat(properties.getValue()).isEqualTo("legacy");
        assertThat(output)
                .contains("[DEPRECATED CONFIG] studio.features.example.* is deprecated")
                .contains("Use studio.example.* instead");
    }

    @Test
    void getBooleanWithLegacyFallbackUsesTargetBeforeLegacy(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("studio.example.enabled", "false")
                .withProperty("studio.features.example.enabled", "true");

        boolean enabled = ConfigurationPropertyMigration.getBooleanWithLegacyFallback(
                environment,
                "studio.example.enabled",
                "studio.features.example.enabled",
                true,
                LoggerFactory.getLogger(getClass()),
                "Example migration.");

        assertThat(enabled).isFalse();
        assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
    }

    static class ExampleProperties {

        private String value;

        String getValue() {
            return value;
        }

        void setValue(String value) {
            this.value = value;
        }
    }
}
