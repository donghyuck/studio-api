package studio.one.platform.thumbnail.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;
import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.extractor.FileParser;
import studio.one.platform.textract.extractor.FileParserFactory;
import studio.one.platform.textract.model.ParsedFile;
import studio.one.platform.textract.service.FileContentExtractionService;
import studio.one.platform.thumbnail.ThumbnailGenerationOptions;
import studio.one.platform.thumbnail.ThumbnailGenerationService;
import studio.one.platform.thumbnail.ThumbnailOptions;
import studio.one.platform.thumbnail.ThumbnailRenderer;
import studio.one.platform.thumbnail.ThumbnailRendererFactory;
import studio.one.platform.thumbnail.ThumbnailResult;
import studio.one.platform.thumbnail.ThumbnailSource;
import studio.one.platform.thumbnail.renderer.DocxThumbnailRenderer;
import studio.one.platform.thumbnail.renderer.ImageThumbnailRenderer;
import studio.one.platform.thumbnail.renderer.PdfThumbnailRenderer;
import studio.one.platform.thumbnail.renderer.PptxThumbnailRenderer;

@ExtendWith(OutputCaptureExtension.class)
class ThumbnailAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ValidationAutoConfiguration.class,
                    ThumbnailAutoConfiguration.class,
                    ThumbnailTextractPreviewAutoConfiguration.class));

    @BeforeEach
    void resetWarnings() throws Exception {
        Field warned = ConfigurationPropertyMigration.class.getDeclaredField("WARNED");
        warned.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> values = (Set<String>) warned.get(null);
        values.clear();
    }

    @Test
    void featureDisabledDoesNotRegisterService() {
        contextRunner
                .withPropertyValues("studio.features.thumbnail.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ThumbnailGenerationService.class);
                    assertThat(context).doesNotHaveBean(ThumbnailRendererFactory.class);
                });
    }

    @Test
    void createsServiceWithHumanReadableMaxSourceSize(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "studio.thumbnail.default-size=96",
                        "studio.thumbnail.max-source-size=10M",
                        "studio.thumbnail.max-source-pixels=1000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ThumbnailGenerationService.class);
                    ThumbnailGenerationOptions options = context.getBean(ThumbnailGenerationService.class)
                            .generationOptions();
                    assertThat(options.defaultSize()).isEqualTo(96);
                    assertThat(options.maxSourceBytes()).isEqualTo(10 * 1024 * 1024);
                    assertThat(options.maxSourcePixels()).isEqualTo(1000);
                    assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
                });
    }

    @Test
    void acceptsLowercaseHumanReadableMaxSourceSize() {
        contextRunner
                .withPropertyValues("studio.thumbnail.max-source-size=10mb")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ThumbnailGenerationOptions options = context.getBean(ThumbnailGenerationService.class)
                            .generationOptions();
                    assertThat(options.maxSourceBytes()).isEqualTo(10 * 1024 * 1024);
                });
    }

    @Test
    void pdfRendererIsOptIn() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ImageThumbnailRenderer.class);
                    assertThat(context).doesNotHaveBean(PdfThumbnailRenderer.class);
                });
    }

    @Test
    void pdfRendererIsRegisteredWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("studio.thumbnail.renderers.pdf.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PdfThumbnailRenderer.class);
                });
    }

    @Test
    void pdfRendererIsConditionalOnPdfboxClasspath() {
        contextRunner
                .withPropertyValues("studio.thumbnail.renderers.pdf.enabled=true")
                .withClassLoader(new FilteredClassLoader(PDDocument.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ImageThumbnailRenderer.class);
                    assertThat(context).doesNotHaveBean(PdfThumbnailRenderer.class);
                });
    }

    @Test
    void documentRenderersAreOptIn() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(PptxThumbnailRenderer.class);
                    assertThat(context).doesNotHaveBean(TextractDocumentPreviewThumbnailRenderer.class);
                });
    }

    @Test
    void pptxRendererIsRegisteredWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("studio.thumbnail.renderers.pptx.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PptxThumbnailRenderer.class);
                });
    }

    @Test
    void pptxRendererIsConditionalOnPoiClasspath() {
        contextRunner
                .withPropertyValues("studio.thumbnail.renderers.pptx.enabled=true")
                .withClassLoader(new FilteredClassLoader(XMLSlideShow.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(PptxThumbnailRenderer.class);
                });
    }

    @Test
    void textractPreviewRenderersRequireTextractServiceBean() {
        contextRunner
                .withPropertyValues(
                        "studio.thumbnail.renderers.docx.enabled=true",
                        "studio.thumbnail.renderers.hwp.enabled=true",
                        "studio.thumbnail.renderers.hwpx.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(TextractDocumentPreviewThumbnailRenderer.class);
                });
    }

    @Test
    void textractPreviewAutoConfigurationIsSafeWhenTextractClasspathIsMissing() {
        contextRunner
                .withPropertyValues("studio.thumbnail.renderers.docx.enabled=true")
                .withClassLoader(new FilteredClassLoader(FileContentExtractionService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(TextractDocumentPreviewThumbnailRenderer.class);
                });
    }

    @Test
    void textractPreviewRenderersAreRegisteredWhenExplicitlyEnabledAndTextractServiceExists() {
        contextRunner
                .withBean(FileContentExtractionService.class, ThumbnailAutoConfigurationTest::textExtractionService)
                .withPropertyValues(
                        "studio.thumbnail.renderers.docx.enabled=true",
                        "studio.thumbnail.renderers.hwp.enabled=true",
                        "studio.thumbnail.renderers.hwpx.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(TextractDocumentPreviewThumbnailRenderer.class))
                            .hasSize(3);
                });
    }

    @Test
    void customDocxRendererByTypePreventsDefaultPreviewRenderer() {
        DocxThumbnailRenderer customRenderer = new TestDocxRenderer();

        contextRunner
                .withBean("customDocxRenderer", DocxThumbnailRenderer.class, () -> customRenderer)
                .withBean(FileContentExtractionService.class, ThumbnailAutoConfigurationTest::textExtractionService)
                .withPropertyValues("studio.thumbnail.renderers.docx.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DocxThumbnailRenderer.class);
                    assertThat(context).getBean(DocxThumbnailRenderer.class).isSameAs(customRenderer);
                });
    }

    @Test
    void textractPreviewRendererGeneratesPngFromParsedText() throws Exception {
        DocxThumbnailRenderer renderer =
                TextractDocumentPreviewThumbnailRenderer.docx(textExtractionService());

        ThumbnailResult result = renderer.render(
                new ThumbnailSource(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "sample.docx",
                        "docx".getBytes()),
                new ThumbnailOptions(96, "png", 25_000_000, 1024 * 1024));

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(javax.imageio.ImageIO.read(new ByteArrayInputStream(result.bytes())).getWidth())
                .isLessThanOrEqualTo(96);
    }

    @Test
    void preservesUserDefinedRendererAndGenerationService() {
        ThumbnailRenderer customRenderer = new TestRenderer();
        ThumbnailGenerationService customService = new ThumbnailGenerationService(
                new ThumbnailRendererFactory(List.of(customRenderer)),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024, 25_000_000));

        contextRunner
                .withBean("customThumbnailRenderer", ThumbnailRenderer.class, () -> customRenderer)
                .withBean(ThumbnailGenerationService.class, () -> customService)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).getBean(ThumbnailGenerationService.class).isSameAs(customService);
                    assertThat(context).getBeans(ThumbnailRenderer.class).containsValue(customRenderer);
                });
    }

    @Test
    void preservesUserDefinedImageRendererByTypeEvenWithDifferentBeanName() {
        ImageThumbnailRenderer customRenderer = new ImageThumbnailRenderer();

        contextRunner
                .withBean("customImageRenderer", ImageThumbnailRenderer.class, () -> customRenderer)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ImageThumbnailRenderer.class);
                    assertThat(context).getBean(ImageThumbnailRenderer.class).isSameAs(customRenderer);
                    assertThat(context.getBeansOfType(ThumbnailRenderer.class)).containsValue(customRenderer);
                });
    }

    @Test
    void legacyAttachmentDefaultsFallbackAndWarn(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "studio.attachment.thumbnail.default-size=96",
                        "studio.attachment.thumbnail.default-format=png")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ThumbnailGenerationOptions options = context.getBean(ThumbnailGenerationService.class)
                            .generationOptions();
                    assertThat(options.defaultSize()).isEqualTo(96);
                    assertThat(output)
                            .contains("[DEPRECATED CONFIG] studio.attachment.thumbnail.default-size is deprecated")
                            .contains("Use studio.thumbnail.default-size instead")
                            .contains("[DEPRECATED CONFIG] studio.attachment.thumbnail.default-format is deprecated")
                            .contains("Use studio.thumbnail.default-format instead");
                });
    }

    @Test
    void targetDefaultsWinOverLegacyWithoutWarning(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "studio.thumbnail.default-size=256",
                        "studio.attachment.thumbnail.default-size=96",
                        "studio.features.attachment.thumbnail.default-size=64")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ThumbnailGenerationOptions options = context.getBean(ThumbnailGenerationService.class)
                            .generationOptions();
                    assertThat(options.defaultSize()).isEqualTo(256);
                    assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
                });
    }

    @Test
    void olderFeatureAttachmentDefaultsFallbackAndWarn(CapturedOutput output) {
        contextRunner
                .withPropertyValues("studio.features.attachment.thumbnail.default-size=64")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ThumbnailGenerationOptions options = context.getBean(ThumbnailGenerationService.class)
                            .generationOptions();
                    assertThat(options.defaultSize()).isEqualTo(64);
                    assertThat(output)
                            .contains("[DEPRECATED CONFIG] studio.features.attachment.thumbnail.default-size is deprecated")
                            .contains("Use studio.thumbnail.default-size instead");
                });
    }

    private static class TestRenderer implements ThumbnailRenderer {
        @Override
        public boolean supports(studio.one.platform.thumbnail.ThumbnailSource source) {
            return false;
        }

        @Override
        public studio.one.platform.thumbnail.ThumbnailResult render(
                studio.one.platform.thumbnail.ThumbnailSource source,
                studio.one.platform.thumbnail.ThumbnailOptions options) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestDocxRenderer extends TestRenderer implements DocxThumbnailRenderer {
    }

    private static FileContentExtractionService textExtractionService() {
        FileParser parser = new FileParser() {
            @Override
            public boolean supports(String contentType, String filename) {
                return true;
            }

            @Override
            public String parse(byte[] bytes, String contentType, String filename) {
                return "Hello document\nSecond line";
            }

            @Override
            public ParsedFile parseStructured(byte[] bytes, String contentType, String filename) {
                return ParsedFile.textOnly(DocumentFormat.DOCX, parse(bytes, contentType, filename), filename);
            }
        };
        return new FileContentExtractionService(new FileParserFactory(List.of(parser)));
    }
}
