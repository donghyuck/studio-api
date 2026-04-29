package studio.one.platform.thumbnail.autoconfigure;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.thumbnail.ThumbnailGenerationOptions;
import studio.one.platform.thumbnail.ThumbnailGenerationService;
import studio.one.platform.thumbnail.ThumbnailRenderer;
import studio.one.platform.thumbnail.ThumbnailRendererFactory;
import studio.one.platform.thumbnail.renderer.ImageThumbnailRenderer;
import studio.one.platform.thumbnail.renderer.PdfThumbnailRenderer;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@AutoConfiguration
@EnableConfigurationProperties(ThumbnailProperties.class)
@Conditional(ThumbnailFeatureCondition.class)
@Slf4j
@RequiredArgsConstructor
public class ThumbnailAutoConfiguration {

    protected static final String FEATURE_NAME = "Thumbnail";

    private final ThumbnailProperties props;
    private final Environment environment;
    private final ObjectProvider<I18n> i18nProvider;

    @Bean
    @Order(100)
    @ConditionalOnProperty(prefix = "studio.thumbnail.renderers.image", name = "enabled", matchIfMissing = true)
    @ConditionalOnMissingBean(ImageThumbnailRenderer.class)
    ImageThumbnailRenderer imageThumbnailRenderer() {
        logCreated(ImageThumbnailRenderer.class);
        return new ImageThumbnailRenderer();
    }

    @Bean
    @Order(200)
    @ConditionalOnClass(name = "org.apache.pdfbox.pdmodel.PDDocument")
    @ConditionalOnProperty(prefix = "studio.thumbnail.renderers.pdf", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(PdfThumbnailRenderer.class)
    PdfThumbnailRenderer pdfThumbnailRenderer() {
        logCreated(PdfThumbnailRenderer.class);
        return new PdfThumbnailRenderer(props.getRenderers().getPdf().getPage());
    }

    @Bean
    @ConditionalOnMissingBean
    ThumbnailRendererFactory thumbnailRendererFactory(ObjectProvider<ThumbnailRenderer> rendererProvider) {
        List<ThumbnailRenderer> renderers = rendererProvider.orderedStream().toList();
        logCreated(ThumbnailRendererFactory.class);
        return new ThumbnailRendererFactory(renderers);
    }

    @Bean
    @ConditionalOnMissingBean
    ThumbnailGenerationService thumbnailGenerationService(ThumbnailRendererFactory rendererFactory) {
        ThumbnailGenerationOptions options = props.generationOptions(environment, log);
        logCreated(ThumbnailGenerationService.class);
        return new ThumbnailGenerationService(rendererFactory, options);
    }

    private void logCreated(Class<?> type) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(type, true), LogUtils.red(State.CREATED.toString())));
    }
}
