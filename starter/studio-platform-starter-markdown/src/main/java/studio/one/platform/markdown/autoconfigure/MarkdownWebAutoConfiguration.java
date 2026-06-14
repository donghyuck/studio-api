package studio.one.platform.markdown.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import studio.one.platform.markdown.application.MarkdownDocumentService;
import studio.one.platform.markdown.web.MarkdownDocumentController;

@AutoConfiguration(after = MarkdownAutoConfiguration.class)
@ConditionalOnProperty(prefix = "studio.markdown", name = "enabled", havingValue = "true")
@ConditionalOnBean(MarkdownDocumentService.class)
@Import(MarkdownDocumentController.class)
public class MarkdownWebAutoConfiguration {
}
