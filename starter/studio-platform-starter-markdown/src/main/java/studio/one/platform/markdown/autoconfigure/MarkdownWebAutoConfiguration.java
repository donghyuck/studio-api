package studio.one.platform.markdown.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import studio.one.platform.markdown.application.MarkdownDocumentService;
import studio.one.platform.markdown.web.MarkdownDocumentController;
import studio.one.platform.markdown.web.DocumentMetadataSchemaController;
import studio.one.platform.markdown.web.MarkdownDocumentMetadataController;

@AutoConfiguration(after = MarkdownAutoConfiguration.class)
@ConditionalOnProperty(prefix = "studio.markdown", name = "enabled", havingValue = "true")
@ConditionalOnBean(MarkdownDocumentService.class)
@Import({
        MarkdownDocumentController.class,
        DocumentMetadataSchemaController.class,
        MarkdownDocumentMetadataController.class
})
public class MarkdownWebAutoConfiguration {
}
