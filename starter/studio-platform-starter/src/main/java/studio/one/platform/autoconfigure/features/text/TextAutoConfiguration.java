package studio.one.platform.autoconfigure.features.text;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;
import studio.one.platform.text.extractor.FileParser;
import studio.one.platform.text.extractor.FileParserFactory;
import studio.one.platform.text.extractor.impl.DocxFileParser;
import studio.one.platform.text.extractor.impl.HtmlFileParser;
import studio.one.platform.text.extractor.impl.ImageFileParser;
import studio.one.platform.text.extractor.impl.PdfFileParser;
import studio.one.platform.text.extractor.impl.PptxFileParser;
import studio.one.platform.text.extractor.impl.TextFileParser;
import studio.one.platform.text.service.FileContentExtractionService;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@AutoConfiguration
@EnableConfigurationProperties({ TextFeatureProperties.class })
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX
        + ".text", name = "enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
@RequiredArgsConstructor
public class TextAutoConfiguration {

    protected static final String FEATURE_NAME = "Text";

    private final TextFeatureProperties props;
    private final ObjectProvider<I18n> i18nProvider;

    @Bean
    public FileParser textFileParser() {
                I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(TextFileParser.class, true), LogUtils.red(State.CREATED.toString())));
        return new TextFileParser();
    }

    @Bean
    @ConditionalOnClass(name = "org.jsoup.Jsoup")
    public FileParser htmlFileParser() {
                I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(HtmlFileParser.class, true), LogUtils.red(State.CREATED.toString())));
        return new HtmlFileParser();
    }

    @Bean
    @ConditionalOnClass(name = "org.apache.pdfbox.pdmodel.PDDocument")
    public FileParser pdfFileParser() {
                I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(PdfFileParser.class, true), LogUtils.red(State.CREATED.toString())));
        return new PdfFileParser();
    }

    @Bean 
    @ConditionalOnClass(name = "net.sourceforge.tess4j.Tesseract")
    public FileParser imageFileParser() {
                I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(ImageFileParser.class, true), LogUtils.red(State.CREATED.toString())));
        return new ImageFileParser(props.getTesseract().getDatapath(), props.getTesseract().getLanguage());
    }

    @Bean
    @ConditionalOnClass(name = "org.apache.poi.xwpf.usermodel.XWPFDocument")
    public FileParser docxFileParser() {
                I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(DocxFileParser.class, true), LogUtils.red(State.CREATED.toString())));
        return new DocxFileParser();
    }

    @Bean
    @ConditionalOnClass(name = "org.apache.poi.xwpf.usermodel.XWPFDocument")
    public FileParser pptxFileParser() {
                I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(PptxFileParser.class, true), LogUtils.red(State.CREATED.toString())));
        return new PptxFileParser();
    }

    @Bean
    public FileParserFactory fileParserFactory(List<FileParser> parsers) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(FileParserFactory.class, true), LogUtils.red(State.CREATED.toString())));
        return new FileParserFactory(parsers);
    }

    @Bean
    public FileContentExtractionService fileContentExtractionService(FileParserFactory factory) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(FileContentExtractionService.class, true), LogUtils.red(State.CREATED.toString())));
        return new FileContentExtractionService(factory);
    }
}
