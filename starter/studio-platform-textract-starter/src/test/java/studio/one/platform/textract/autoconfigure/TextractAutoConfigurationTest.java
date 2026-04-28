package studio.one.platform.textract.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;
import studio.one.platform.textract.service.FileContentExtractionService;

@ExtendWith(OutputCaptureExtension.class)
class TextractAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ValidationAutoConfiguration.class,
                    TextractAutoConfiguration.class))
            .withPropertyValues("studio.features.textract.enabled=true");

    @BeforeEach
    void resetWarnings() throws Exception {
        Field warned = ConfigurationPropertyMigration.class.getDeclaredField("WARNED");
        warned.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> values = (Set<String>) warned.get(null);
        values.clear();
    }

    @Test
    void rejectsNonPositiveMaxExtractSizeAtStartup() {
        contextRunner
                .withPropertyValues("studio.textract.max-extract-size=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("studio.textract.max-extract-size")
                            .hasMessageContaining("between 1B")
                            .hasMessageContaining("2147483647B");
                });
    }

    @Test
    void createsExtractionServiceWhenMaxExtractSizeUsesHumanReadableUnit(CapturedOutput output) {
        contextRunner
                .withPropertyValues("studio.textract.max-extract-size=10M")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TextractProperties.class);
                    assertThat(context).hasSingleBean(FileContentExtractionService.class);
                    FileContentExtractionService service = context.getBean(FileContentExtractionService.class);
                    assertThat(ReflectionTestUtils.getField(service, "maxExtractBytes"))
                            .isEqualTo(10 * 1024 * 1024);
                    assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
                });
    }

    @Test
    void legacyRuntimeMaxExtractSizeFallsBackAndWarns(CapturedOutput output) {
        contextRunner
                .withPropertyValues("studio.text.max-extract-size=10M")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FileContentExtractionService service = context.getBean(FileContentExtractionService.class);
                    assertThat(ReflectionTestUtils.getField(service, "maxExtractBytes"))
                            .isEqualTo(10 * 1024 * 1024);
                    assertThat(output)
                            .contains("[DEPRECATED CONFIG] studio.text.max-extract-size is deprecated")
                            .contains("Use studio.textract.max-extract-size instead");
                });
    }

    @Test
    void legacyFeatureMaxExtractBytesFallsBackAndWarns(CapturedOutput output) {
        contextRunner
                .withPropertyValues("studio.features.text.max-extract-bytes=1024")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FileContentExtractionService.class);
                    FileContentExtractionService service = context.getBean(FileContentExtractionService.class);
                    assertThat(ReflectionTestUtils.getField(service, "maxExtractBytes")).isEqualTo(1024);
                    assertThat(output)
                            .contains("[DEPRECATED CONFIG] studio.features.text.max-extract-bytes is deprecated")
                            .contains("Use studio.textract.max-extract-size instead");
                });
    }

    @Test
    void targetMaxExtractSizeTakesPriorityOverLegacy(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "studio.textract.max-extract-size=10M",
                        "studio.text.max-extract-size=1",
                        "studio.features.text.max-extract-bytes=1024")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FileContentExtractionService service = context.getBean(FileContentExtractionService.class);
                    assertThat(ReflectionTestUtils.getField(service, "maxExtractBytes"))
                            .isEqualTo(10 * 1024 * 1024);
                    assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
                });
    }

    @Test
    void legacyTesseractPropertiesFallBackAndWarn(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "studio.features.text.tesseract.datapath=/legacy/tessdata",
                        "studio.features.text.tesseract.language=eng")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ReflectionTestUtils.invokeMethod(context.getBean(TextractAutoConfiguration.class),
                            "resolveTesseractProperties");
                    TextractProperties properties = context.getBean(TextractProperties.class);
                    assertThat(properties.getTesseract().getDatapath()).isEqualTo("/legacy/tessdata");
                    assertThat(properties.getTesseract().getLanguage()).isEqualTo("eng");
                    assertThat(output)
                            .contains("[DEPRECATED CONFIG] studio.features.text.tesseract.datapath is deprecated")
                            .contains("[DEPRECATED CONFIG] studio.features.text.tesseract.language is deprecated")
                            .contains("Use studio.textract.tesseract.datapath instead")
                            .contains("Use studio.textract.tesseract.language instead");
                });
    }

    @Test
    void legacyRuntimeTesseractPropertiesFallBackAndWarn(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "studio.text.tesseract.datapath=/legacy-runtime/tessdata",
                        "studio.text.tesseract.language=eng")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ReflectionTestUtils.invokeMethod(context.getBean(TextractAutoConfiguration.class),
                            "resolveTesseractProperties");
                    TextractProperties properties = context.getBean(TextractProperties.class);
                    assertThat(properties.getTesseract().getDatapath()).isEqualTo("/legacy-runtime/tessdata");
                    assertThat(properties.getTesseract().getLanguage()).isEqualTo("eng");
                    assertThat(output)
                            .contains("[DEPRECATED CONFIG] studio.text.tesseract.datapath is deprecated")
                            .contains("[DEPRECATED CONFIG] studio.text.tesseract.language is deprecated")
                            .contains("Use studio.textract.tesseract.datapath instead")
                            .contains("Use studio.textract.tesseract.language instead");
                });
    }

    @Test
    void targetTesseractPropertiesTakePriorityOverLegacy(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "studio.textract.tesseract.datapath=/target/tessdata",
                        "studio.textract.tesseract.language=kor+eng",
                        "studio.text.tesseract.datapath=/legacy-runtime/tessdata",
                        "studio.text.tesseract.language=jpn",
                        "studio.features.text.tesseract.datapath=/legacy/tessdata",
                        "studio.features.text.tesseract.language=eng")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ReflectionTestUtils.invokeMethod(context.getBean(TextractAutoConfiguration.class),
                            "resolveTesseractProperties");
                    TextractProperties properties = context.getBean(TextractProperties.class);
                    assertThat(properties.getTesseract().getDatapath()).isEqualTo("/target/tessdata");
                    assertThat(properties.getTesseract().getLanguage()).isEqualTo("kor+eng");
                    assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
                });
    }

    @Test
    void legacyFeatureEnabledFallsBackAndWarns(CapturedOutput output) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ValidationAutoConfiguration.class,
                        TextractAutoConfiguration.class))
                .withPropertyValues("studio.features.text.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FileContentExtractionService.class);
                    assertThat(output)
                            .contains("[DEPRECATED CONFIG] studio.features.text.enabled is deprecated")
                            .contains("Use studio.features.textract.enabled instead");
                });
    }

    @Test
    void targetFeatureDisabledTakesPriorityOverLegacyEnabled(CapturedOutput output) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ValidationAutoConfiguration.class,
                        TextractAutoConfiguration.class))
                .withPropertyValues(
                        "studio.features.textract.enabled=false",
                        "studio.features.text.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(FileContentExtractionService.class);
                    assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
                });
    }

    @Test
    void rejectsNonPositiveLegacyMaxExtractBytesAtStartup() {
        contextRunner
                .withPropertyValues("studio.features.text.max-extract-bytes=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("studio.features.text.max-extract-bytes")
                            .rootCause()
                            .hasMessageContaining("between 1B");
                });
    }

    @Test
    void createsExtractionServiceWithDefaultLimit() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TextractProperties.class);
                    assertThat(context).hasSingleBean(FileContentExtractionService.class);
                    FileContentExtractionService service = context.getBean(FileContentExtractionService.class);
                    assertThat(ReflectionTestUtils.getField(service, "maxExtractBytes"))
                            .isEqualTo(10 * 1024 * 1024);
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
