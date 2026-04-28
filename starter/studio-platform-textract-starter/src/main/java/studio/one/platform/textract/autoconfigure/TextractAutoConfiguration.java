package studio.one.platform.textract.autoconfigure;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.textract.extractor.FileParser;
import studio.one.platform.textract.extractor.FileParserFactory;
import studio.one.platform.textract.extractor.impl.DocxFileParser;
import studio.one.platform.textract.extractor.impl.HtmlFileParser;
import studio.one.platform.textract.extractor.impl.HwpHwpxFileParser;
import studio.one.platform.textract.extractor.impl.ImageFileParser;
import studio.one.platform.textract.extractor.impl.PdfFileParser;
import studio.one.platform.textract.extractor.impl.PptxFileParser;
import studio.one.platform.textract.extractor.impl.TextFileParser;
import studio.one.platform.textract.service.FileContentExtractionService;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@AutoConfiguration
@EnableConfigurationProperties(TextractProperties.class)
@Conditional(TextractFeatureCondition.class)
@Slf4j
@RequiredArgsConstructor
public class TextractAutoConfiguration {

    protected static final String FEATURE_NAME = "Text";
    private static final String MIGRATION_REASON = "Textract runtime policy now belongs to studio.textract.*, "
            + "while studio.features.textract.* is reserved for feature wiring.";

    private final TextractProperties props;
    private final Environment environment;
    private final ObjectProvider<I18n> i18nProvider;

    @Bean
    public FileParser textFileParser() {
        logCreated(TextFileParser.class);
        return new TextFileParser();
    }

    @Bean
    @ConditionalOnClass(name = "org.jsoup.Jsoup")
    public FileParser htmlFileParser() {
        logCreated(HtmlFileParser.class);
        return new HtmlFileParser();
    }

    @Bean
    @ConditionalOnClass(name = "org.apache.pdfbox.pdmodel.PDDocument")
    public FileParser pdfFileParser() {
        logCreated(PdfFileParser.class);
        return new PdfFileParser();
    }

    @Bean
    @ConditionalOnClass(name = "net.sourceforge.tess4j.Tesseract")
    public FileParser imageFileParser() {
        logCreated(ImageFileParser.class);
        TextractProperties.Tesseract tesseract = resolveTesseractProperties();
        return new ImageFileParser(tesseract.getDatapath(), tesseract.getLanguage());
    }

    @Bean
    @ConditionalOnClass(name = "org.apache.poi.xwpf.usermodel.XWPFDocument")
    public FileParser docxFileParser() {
        logCreated(DocxFileParser.class);
        return new DocxFileParser();
    }

    @Bean
    @ConditionalOnClass(name = "org.apache.poi.xslf.usermodel.XMLSlideShow")
    public FileParser pptxFileParser() {
        logCreated(PptxFileParser.class);
        return new PptxFileParser();
    }

    @Bean
    @ConditionalOnClass(name = "org.apache.poi.poifs.filesystem.POIFSFileSystem")
    public FileParser hwpHwpxFileParser() {
        logCreated(HwpHwpxFileParser.class);
        return new HwpHwpxFileParser();
    }

    @Bean
    public FileParserFactory fileParserFactory(List<FileParser> parsers) {
        logCreated(FileParserFactory.class);
        return new FileParserFactory(parsers);
    }

    @Bean
    public FileContentExtractionService fileContentExtractionService(FileParserFactory factory) {
        logCreated(FileContentExtractionService.class);
        return new FileContentExtractionService(factory, resolveMaxExtractBytes());
    }

    private int resolveMaxExtractBytes() {
        String targetValue = Binder.get(environment)
                .bind(TextractProperties.MAX_EXTRACT_SIZE_PROPERTY, String.class)
                .orElse(null);
        if (StringUtils.hasText(targetValue)) {
            return TextractProperties.parseToBytes(targetValue, TextractProperties.MAX_EXTRACT_SIZE_PROPERTY);
        }

        String legacyValue = Binder.get(environment)
                .bind(TextractProperties.LEGACY_RUNTIME_MAX_EXTRACT_SIZE_PROPERTY, String.class)
                .orElse(null);
        if (StringUtils.hasText(legacyValue)) {
            ConfigurationPropertyMigration.warnDeprecated(log,
                    TextractProperties.LEGACY_RUNTIME_MAX_EXTRACT_SIZE_PROPERTY,
                    TextractProperties.MAX_EXTRACT_SIZE_PROPERTY,
                    MIGRATION_REASON);
            return TextractProperties.parseToBytes(
                    legacyValue, TextractProperties.LEGACY_RUNTIME_MAX_EXTRACT_SIZE_PROPERTY);
        }

        legacyValue = Binder.get(environment)
                .bind(TextractProperties.LEGACY_FEATURE_MAX_EXTRACT_BYTES_PROPERTY, String.class)
                .orElse(null);
        if (StringUtils.hasText(legacyValue)) {
            ConfigurationPropertyMigration.warnDeprecated(log,
                    TextractProperties.LEGACY_FEATURE_MAX_EXTRACT_BYTES_PROPERTY,
                    TextractProperties.MAX_EXTRACT_SIZE_PROPERTY,
                    MIGRATION_REASON);
            return TextractProperties.parseToBytes(
                    legacyValue, TextractProperties.LEGACY_FEATURE_MAX_EXTRACT_BYTES_PROPERTY);
        }

        return props.getMaxExtractBytes();
    }

    private TextractProperties.Tesseract resolveTesseractProperties() {
        TextractProperties.Tesseract tesseract = props.getTesseract();
        String targetPrefix = TextractProperties.PREFIX + ".tesseract";
        String legacyRuntimePrefix = TextractProperties.LEGACY_RUNTIME_PREFIX + ".tesseract";
        String legacyFeaturePrefix = TextractProperties.LEGACY_FEATURE_PREFIX + ".tesseract";
        bindTesseractFallback(targetPrefix, legacyRuntimePrefix, legacyFeaturePrefix,
                "datapath", tesseract::setDatapath);
        bindTesseractFallback(targetPrefix, legacyRuntimePrefix, legacyFeaturePrefix,
                "language", tesseract::setLanguage);
        return tesseract;
    }

    private void bindTesseractFallback(
            String targetPrefix,
            String legacyRuntimePrefix,
            String legacyFeaturePrefix,
            String propertyName,
            java.util.function.Consumer<String> setter) {
        String targetKey = targetPrefix + "." + propertyName;
        if (environment.containsProperty(targetKey)) {
            return;
        }
        String legacyRuntimeKey = legacyRuntimePrefix + "." + propertyName;
        String runtimeValue = Binder.get(environment).bind(legacyRuntimeKey, String.class).orElse(null);
        if (StringUtils.hasText(runtimeValue)) {
            setter.accept(runtimeValue);
            ConfigurationPropertyMigration.warnDeprecated(log, legacyRuntimeKey, targetKey, MIGRATION_REASON);
            return;
        }
        String legacyFeatureKey = legacyFeaturePrefix + "." + propertyName;
        Binder.get(environment).bind(legacyFeatureKey, String.class).ifBound(value -> {
            setter.accept(value);
            ConfigurationPropertyMigration.warnDeprecated(log, legacyFeatureKey, targetKey, MIGRATION_REASON);
        });
    }

    private void logCreated(Class<?> type) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(type, true), LogUtils.red(State.CREATED.toString())));
    }
}
