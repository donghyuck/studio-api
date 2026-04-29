package studio.one.platform.thumbnail.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import studio.one.platform.textract.service.FileContentExtractionService;
import studio.one.platform.thumbnail.renderer.DocxThumbnailRenderer;
import studio.one.platform.thumbnail.renderer.HwpThumbnailRenderer;
import studio.one.platform.thumbnail.renderer.HwpxThumbnailRenderer;

@AutoConfiguration(after = ThumbnailAutoConfiguration.class)
@ConditionalOnClass(name = "studio.one.platform.textract.service.FileContentExtractionService")
@ConditionalOnBean(type = "studio.one.platform.textract.service.FileContentExtractionService")
public class ThumbnailTextractPreviewAutoConfiguration {

    @Bean
    @Order(400)
    @ConditionalOnProperty(prefix = "studio.thumbnail.renderers.docx", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(DocxThumbnailRenderer.class)
    DocxThumbnailRenderer docxThumbnailRenderer(FileContentExtractionService extractionService) {
        return TextractDocumentPreviewThumbnailRenderer.docx(extractionService);
    }

    @Bean
    @Order(410)
    @ConditionalOnProperty(prefix = "studio.thumbnail.renderers.hwp", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(HwpThumbnailRenderer.class)
    HwpThumbnailRenderer hwpThumbnailRenderer(FileContentExtractionService extractionService) {
        return TextractDocumentPreviewThumbnailRenderer.hwp(extractionService);
    }

    @Bean
    @Order(420)
    @ConditionalOnProperty(prefix = "studio.thumbnail.renderers.hwpx", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(HwpxThumbnailRenderer.class)
    HwpxThumbnailRenderer hwpxThumbnailRenderer(FileContentExtractionService extractionService) {
        return TextractDocumentPreviewThumbnailRenderer.hwpx(extractionService);
    }
}
