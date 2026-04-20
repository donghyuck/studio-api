package studio.one.platform.textract.autoconfigure;

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
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX
        + ".text", name = "enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
@RequiredArgsConstructor
public class TextractAutoConfiguration {

    protected static final String FEATURE_NAME = "Text";

    private final TextractProperties props;
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
        return new ImageFileParser(props.getTesseract().getDatapath(), props.getTesseract().getLanguage());
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
        return new FileContentExtractionService(factory, props.getMaxExtractBytes());
    }

    private void logCreated(Class<?> type) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(type, true), LogUtils.red(State.CREATED.toString())));
    }
}
