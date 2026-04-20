package studio.one.platform.autoconfigure.features.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.platform.text.service.FileContentExtractionService;
import studio.one.platform.textract.autoconfigure.TextractAutoConfiguration;

class TextAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ValidationAutoConfiguration.class,
                    TextractAutoConfiguration.class,
                    TextAutoConfiguration.class))
            .withPropertyValues("studio.features.text.enabled=true");

    @Test
    void createsLegacyExtractionServiceBridge() {
        contextRunner
                .withPropertyValues("studio.features.text.max-extract-bytes=1024")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(studio.one.platform.textract.service.FileContentExtractionService.class);
                    assertThat(context).hasSingleBean(FileContentExtractionService.class);
                });
    }
}
