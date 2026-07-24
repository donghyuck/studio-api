package studio.one.platform.textract.autoconfigure;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
import studio.one.platform.textract.infrastructure.extractor.impl.EpubFileParser;
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
import studio.one.platform.textract.infrastructure.extractor.pdf.HeuristicMathDocumentExtractionEngine;
import studio.one.platform.textract.infrastructure.extractor.pdf.MathDocumentExtractionEngine;
import studio.one.platform.textract.infrastructure.extractor.pdf.MathDocumentOcrClient;
import studio.one.platform.textract.infrastructure.extractor.pdf.MathDocumentOcrExtractionEngine;
import studio.one.platform.textract.infrastructure.extractor.pdf.MathVisionCorrectionClient;
import studio.one.platform.textract.infrastructure.extractor.pdf.KoreanTextOcrClient;
import studio.one.platform.textract.infrastructure.extractor.pdf.pdfbox.PdfBoxExtractionEngine;
import studio.one.platform.textract.infrastructure.extractor.pdf.pdfbox.PdfOcrFallbackOptions;
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
    @ConditionalOnClass(name = "org.jsoup.Jsoup")
    public FileParser epubFileParser() {
        logCreated(EpubFileParser.class);
        TextractProperties.Epub epub = props.getEpub();
        return new EpubFileParser(epub.getMaxEntryBytes(), epub.getMaxExtractedBytes());
    }

    @Bean
    @ConditionalOnClass(name = "org.apache.pdfbox.pdmodel.PDDocument")
    public FileParser pdfFileParser(
            ObjectProvider<PyMuPdf4LlmClient> pyMuPdf4LlmClientProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        logCreated(PdfFileParser.class);
        List<PdfExtractionEngine> engines = new ArrayList<>();
        PdfExtractionEngine pdfBoxEngine = null;
        if (props.getPdf().getEngines().getPdfbox().isEnabled()) {
            pdfBoxEngine = new PdfBoxExtractionEngine(pdfOcrFallbackOptions());
            engines.add(pdfBoxEngine);
        }
        PdfExtractionEngine pyMuPdfEngine = null;
        PyMuPdf4LlmClient pyMuPdf4LlmClient = pyMuPdf4LlmClientProvider.getIfAvailable();
        if (pyMuPdf4LlmClient != null) {
            pyMuPdfEngine = new PyMuPdf4LlmExtractionEngine(pyMuPdf4LlmClient);
            engines.add(pyMuPdfEngine);
        }
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        List<MathDocumentExtractionEngine> mathEngines = new ArrayList<>(mathDocumentExtractionEngines(objectMapper));
        PdfExtractionEngine mathDelegate = pyMuPdfEngine == null ? pdfBoxEngine : pyMuPdfEngine;
        if (mathDelegate != null) {
            mathEngines.add(new HeuristicMathDocumentExtractionEngine(mathDelegate));
        }
        TextractProperties.QualityGate qualityGate = props.getPdf().getEngines().getMath().getQualityGate();
        TextractProperties.Hybrid hybrid = props.getPdf().getEngines().getMath().getHybrid();
        List<MathVisionCorrectionClient> visionClients = mathVisionCorrectionClients(objectMapper);
        TextractProperties.KoreanOcr koreanOcr = props.getPdf().getEngines().getKoreanOcr();
        List<KoreanTextOcrClient> koreanTextOcrClients = koreanTextOcrClients(objectMapper, koreanOcr);
        return new PdfFileParser(new PdfExtractionEngineSelector(engines, null, mathEngines,
                qualityGate.isEnabled(), qualityGate.getMinScore(),
                hybrid.isEnabled(), hybrid.getSamplePages(),
                visionClients, props.getPdf().getEngines().getMath().getVisionCorrection().isEnabled(),
                koreanTextOcrClients, koreanOcr.getMaxPages(),
                new PdfExtractionEngineSelector.MathCorrectionPolicy(hybrid.getMaxCorrectionPages(),
                        hybrid.getFallbackPages(), hybrid.getWaveSize(), hybrid.getTimeBudget())),
                pdfExtractionOptions());
    }

    private List<KoreanTextOcrClient> koreanTextOcrClients(ObjectMapper objectMapper,
            TextractProperties.KoreanOcr properties) {
        if (properties == null || !properties.isEnabled()) {
            return List.of();
        }
        List<KoreanTextOcrClient> clients = new ArrayList<>();
        clients.add(new PaddleOcrKoreanTextClient(true, "paddleocr", properties.getEndpoint(),
                properties.getTimeout(), properties.getMaxFileSizeBytes(), properties.getBatchSize(), objectMapper));
        if (properties.isFallbackEnabled()
                && !properties.getFallbackEndpoint().equals(properties.getEndpoint())) {
            clients.add(new PaddleOcrKoreanTextClient(true, "pymupdf4llm-korean-ocr",
                    properties.getFallbackEndpoint(), properties.getTimeout(), properties.getMaxFileSizeBytes(),
                    properties.getFallbackBatchSize(), objectMapper));
        }
        return List.copyOf(clients);
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
    @ConditionalOnClass(ObjectMapper.class)
    @ConditionalOnProperty(prefix = "studio.textract.pdf.engines.math", name = "enabled", havingValue = "true")
    public MathDocumentOcrClient mathDocumentOcrClient(ObjectProvider<ObjectMapper> objectMapperProvider) {
        logCreated(MathDocumentOcrClient.class);
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        TextractProperties.Math math = props.getPdf().getEngines().getMath();
        return mathDocumentOcrClient(math.getProvider(), objectMapper);
    }

    private List<MathDocumentExtractionEngine> mathDocumentExtractionEngines(ObjectMapper objectMapper) {
        TextractProperties.Math math = props.getPdf().getEngines().getMath();
        if (!math.isEnabled()) {
            return List.of();
        }
        Set<TextractProperties.MathProvider> providers = new LinkedHashSet<>();
        providers.add(math.getProvider());
        if (math.getFallbackProviders() != null) {
            providers.addAll(math.getFallbackProviders());
        }
        List<MathDocumentExtractionEngine> engines = new ArrayList<>();
        for (TextractProperties.MathProvider provider : providers) {
            MathDocumentOcrClient client = mathDocumentOcrClient(provider, objectMapper);
            if (client.available()) {
                engines.add(new MathDocumentOcrExtractionEngine(client));
            }
        }
        return engines;
    }

    private MathDocumentOcrClient mathDocumentOcrClient(
            TextractProperties.MathProvider provider,
            ObjectMapper objectMapper) {
        TextractProperties.Math math = props.getPdf().getEngines().getMath();
        return switch (provider == null ? TextractProperties.MathProvider.NONE : provider) {
            case PIX2TEXT -> {
                TextractProperties.Pix2Text pix2text = math.getPix2text();
                yield new Pix2TextMathDocumentOcrClient(
                        pix2text.getEndpoint(),
                        pix2text.getTimeout(),
                        pix2text.getMaxFileSizeBytes(),
                        pix2text.getLanguage(),
                        pix2text.isPageByPage(),
                        pix2text.getBatchSize(),
                        objectMapper);
            }
            case MATHPIX -> {
                TextractProperties.Mathpix mathpix = math.getMathpix();
                yield new MathpixMathDocumentOcrClient(
                        mathpix.getApiBaseUrl(),
                        mathpix.getTimeout(),
                        mathpix.getPollInterval(),
                        mathpix.getMaxPollAttempts(),
                        mathpix.getAppId(),
                        mathpix.getAppKey(),
                        objectMapper);
            }
            case NONE -> new MathDocumentOcrClient() {
                @Override
                public boolean available() {
                    return false;
                }

                @Override
                public String provider() {
                    return "none";
                }

                @Override
                public studio.one.platform.textract.domain.model.ParsedFile extract(
                        studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionRequest request,
                        studio.one.platform.textract.infrastructure.extractor.pdf.PdfDocumentAnalysis analysis) {
                    throw new studio.one.platform.textract.domain.error.FileParseException(
                            "Math document OCR provider is not configured.");
                }
            };
        };
    }

    private List<MathVisionCorrectionClient> mathVisionCorrectionClients(ObjectMapper objectMapper) {
        TextractProperties.VisionCorrection vision = props.getPdf().getEngines().getMath().getVisionCorrection();
        if (!vision.isEnabled()) {
            return List.of();
        }
        return switch (vision.getProvider() == null ? TextractProperties.VisionProvider.NONE : vision.getProvider()) {
            case GEMINI -> {
                TextractProperties.Gemini gemini = vision.getGemini();
                yield List.of(new GeminiMathVisionCorrectionClient(
                        gemini.getBaseUrl(),
                        gemini.getApiKey(),
                        gemini.getModel(),
                        gemini.getTimeout(),
                        gemini.getMaxFileSizeBytes(),
                        objectMapper));
            }
            case NONE -> List.of();
        };
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
        TextractProperties.LargePdf largePdf = pdf.getLargePdf();
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
                engines.getPymupdf4llm().getMaxFileSizeBytes(),
                largePdf.isEnabled(),
                largePdf.getPageThreshold(),
                largePdf.getBatchSize(),
                largePdf.isContinueOnPartFailure(),
                largePdf.getMaxPartFailures(),
                null,
                null,
                null,
                largePdf.isIncludeImages());
    }

    private PdfOcrFallbackOptions pdfOcrFallbackOptions() {
        TextractProperties.PdfOcrFallback fallback = props.getPdf().getOcrFallback();
        TextractProperties.Tesseract tesseract = resolveTesseractProperties();
        return new PdfOcrFallbackOptions(
                fallback.isEnabled(),
                fallback.getMaxPages(),
                fallback.getDpi(),
                tesseract.getDatapath(),
                tesseract.getLanguage());
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
