package studio.one.platform.autoconfigure.features.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.platform.text.service.FileContentExtractionService;

class TextAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ValidationAutoConfiguration.class,
                    TextAutoConfiguration.class))
            .withPropertyValues("studio.features.text.enabled=true");

    @Test
    void rejectsNonPositiveMaxExtractBytesAtBindingTime() {
        contextRunner
                .withPropertyValues("studio.features.text.max-extract-bytes=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("Could not bind properties to 'TextFeatureProperties'")
                            .rootCause()
                            .hasMessageContaining("maxExtractBytes")
                            .hasMessageContaining("1");
                });
    }

    @Test
    void createsExtractionServiceWhenMaxExtractBytesIsPositive() {
        contextRunner
                .withPropertyValues("studio.features.text.max-extract-bytes=1024")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TextFeatureProperties.class);
                    assertThat(context).hasSingleBean(FileContentExtractionService.class);
                });
    }
}
