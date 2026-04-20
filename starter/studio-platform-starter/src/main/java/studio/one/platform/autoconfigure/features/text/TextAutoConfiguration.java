package studio.one.platform.autoconfigure.features.text;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import studio.one.platform.text.service.FileContentExtractionService;

/**
 * Compatibility bridge for the legacy data-module text extraction API.
 *
 * @deprecated since 2026-04-20. Use
 *             {@link studio.one.platform.textract.autoconfigure.TextractAutoConfiguration}.
 */
@AutoConfiguration(after = studio.one.platform.textract.autoconfigure.TextractAutoConfiguration.class)
@Deprecated(forRemoval = false)
public class TextAutoConfiguration {

    @Bean
    @ConditionalOnBean(studio.one.platform.textract.service.FileContentExtractionService.class)
    @ConditionalOnMissingBean(FileContentExtractionService.class)
    public FileContentExtractionService legacyFileContentExtractionService(
            studio.one.platform.textract.service.FileContentExtractionService delegate) {
        return new FileContentExtractionService(delegate);
    }
}
