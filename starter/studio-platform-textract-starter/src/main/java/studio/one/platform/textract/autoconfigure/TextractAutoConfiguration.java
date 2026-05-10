package studio.one.platform.textract.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.textract.application.usecase.FileParser;
import studio.one.platform.textract.application.usecase.FileParserFactory;
import studio.one.platform.textract.infrastructure.extractor.impl.DocxFileParser;
import studio.one.platform.textract.infrastructure.extractor.impl.ExcelFileParser;
import studio.one.platform.textract.infrastructure.extractor.impl.HtmlFileParser;
import studio.one.platform.textract.infrastructure.extractor.impl.HwpHwpxFileParser;
import studio.one.platform.textract.infrastructure.extractor.impl.ImageFileParser;
import studio.one.platform.textract.infrastructure.extractor.impl.PdfFileParser;
import studio.one.platform.textract.infrastructure.extractor.impl.PptxFileParser;
import studio.one.platform.textract.infrastructure.extractor.impl.TextFileParser;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionEngine;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionEngineSelector;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionMode;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionOptions;
import studio.one.platform.textract.infrastructure.extractor.pdf.pdfbox.PdfBoxExtractionEngine;
import studio.one.platform.textract.infrastructure.extractor.pdf.pymupdf.PyMuPdf4LlmClient;
import studio.one.platform.textract.infrastructure.extractor.pdf.pymupdf.PyMuPdf4LlmExtractionEngine;
import studio.one.platform.textract.application.usecase.FileContentExtractionService;
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
    public FileParser pdfFileParser(ObjectProvider<PyMuPdf4LlmClient> pyMuPdf4LlmClientProvider) {
        logCreated(PdfFileParser.class);
        List<PdfExtractionEngine> engines = new ArrayList<>();
        if (props.getPdf().getEngines().getPdfbox().isEnabled()) {
            engines.add(new PdfBoxExtractionEngine());
        }
        PyMuPdf4LlmClient pyMuPdf4LlmClient = pyMuPdf4LlmClientProvider.getIfAvailable();
        if (pyMuPdf4LlmClient != null) {
            engines.add(new PyMuPdf4LlmExtractionEngine(pyMuPdf4LlmClient));
        }
        return new PdfFileParser(new PdfExtractionEngineSelector(engines), pdfExtractionOptions());
    }

    @Bean
    @ConditionalOnClass(ObjectMapper.class)
    @ConditionalOnProperty(prefix = "studio.textract.pdf.engines.pymupdf4llm", name = "enabled", havingValue = "true")
    public PyMuPdf4LlmClient pyMuPdf4LlmClient(ObjectProvider<ObjectMapper> objectMapperProvider) {
        logCreated(PyMuPdf4LlmClient.class);
        TextractProperties.PyMuPdf4Llm worker = props.getPdf().getEngines().getPymupdf4llm();
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new PyMuPdf4LlmHttpClient(
                worker.getEndpoint(),
                worker.getTimeout(),
                worker.getMaxFileSizeBytes(),
                objectMapper);
    }

    @Bean
    @ConditionalOnClass(name = "net.sourceforge.tess4j.Tesseract")
    public FileParser imageFileParser() {
        logCreated(ImageFileParser.class);
        TextractProperties.Tesseract tesseract = resolveTesseractProperties();
        return new ImageFileParser(tesseract.getDatapath(), tesseract.getLanguage());
    }

    @Bean
    @ConditionalOnClass(name = {
            "org.apache.poi.ss.usermodel.WorkbookFactory",
            "org.apache.poi.xssf.usermodel.XSSFWorkbook"
    })
    public FileParser excelFileParser() {
        logCreated(ExcelFileParser.class);
        return new ExcelFileParser();
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

    private PdfExtractionOptions pdfExtractionOptions() {
        TextractProperties.Pdf pdf = props.getPdf();
        TextractProperties.PreferPyMuPdf4LlmWhen prefer = pdf.getAuto().getPreferPymupdf4llmWhen();
        TextractProperties.PdfEngines engines = pdf.getEngines();
        return new PdfExtractionOptions(
                PdfExtractionMode.valueOf(pdf.getEngine().name()),
                pdf.isFallbackEnabled(),
                engines.getPdfbox().isEnabled(),
                engines.getPymupdf4llm().isEnabled(),
                false,
                false,
                false,
                null,
                prefer.getMinPages(),
                engines.getPymupdf4llm().getMaxFileSizeBytes());
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
