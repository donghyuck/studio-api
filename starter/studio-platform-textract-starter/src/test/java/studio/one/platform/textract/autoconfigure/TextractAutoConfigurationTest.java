package studio.one.platform.textract.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.platform.textract.service.FileContentExtractionService;

class TextractAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ValidationAutoConfiguration.class,
                    TextractAutoConfiguration.class))
            .withPropertyValues("studio.features.text.enabled=true");

    @Test
    void rejectsNonPositiveMaxExtractBytesAtBindingTime() {
        contextRunner
                .withPropertyValues("studio.features.text.max-extract-bytes=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("Could not bind properties to 'TextractProperties'")
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
                    assertThat(context).hasSingleBean(TextractProperties.class);
                    assertThat(context).hasSingleBean(FileContentExtractionService.class);
                    assertThat(context).hasBean("textFileParser");
                    assertThat(context).hasBean("hwpHwpxFileParser");
                });
    }

    @Test
    void skipsPdfParserWhenPdfBoxIsNotAvailable() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.apache.pdfbox"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("pdfFileParser");
                });
    }

    @Test
    void skipsHwpParserWhenPoiPoifsIsNotAvailable() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.apache.poi.poifs"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("hwpHwpxFileParser");
                });
    }
}
